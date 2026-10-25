package dev.intent.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.intent.app.MainActivity
import dev.intent.app.R
import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.AgentListResult
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SubscriptionSpec
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Local alerts exist only while the paired session is connected; no push service is used. */
class LocalNotifications(private val context: Context) {
    init {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(
                    "attention",
                    "Agent attention and questions",
                    NotificationManager.IMPORTANCE_DEFAULT
                ),
                NotificationChannel(
                    "completion",
                    "Task and agent completion",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        )
    }

    fun observe(session: RpcSession, connectionId: String): Closeable {
        val active = AtomicBoolean(true)
        val lease = session.subscribe(
            SubscriptionSpec(
                "events.subscribe",
                buildJsonObject {
                    put(
                        "eventTypes",
                        JsonArray(
                            listOf(
                                "agent:attention-requested",
                                "agent:updated",
                                "agent:idle",
                                "task:status-changed"
                            ).map(::JsonPrimitive)
                        )
                    )
                },
                notificationMethod = "events.event"
            ),
            onMessage = { payload ->
                val alert = notificationTrigger(payload)
                if (alert != null) {
                    val event =
                        payload["event"] as? kotlinx.serialization.json.JsonObject ?: payload
                    if (event.text("type")?.startsWith("agent:") == true) {
                        session.request(
                            "agent.list",
                            buildJsonObject {
                                put("workspaceId", alert.workspaceId)
                            },
                            AgentListResult.serializer()
                        ).whenComplete { result, _ ->
                            if (active.get() &&
                                !suppressAgentAlert(
                                    alert.subjectId,
                                    result?.agents,
                                    event.text("type") == "agent:idle"
                                )
                            ) {
                                post(connectionId, alert)
                            }
                        }
                    } else if (active.get()) {
                        post(connectionId, alert)
                    }
                }
            }
        )
        return Closeable {
            active.set(false)
            lease.close()
        }
    }

    private fun post(connectionId: String, alert: LocalAlert) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val open = Intent(context, MainActivity::class.java)
            .setAction("dev.intent.OPEN_NOTIFICATION")
            .putExtra("connectionId", connectionId)
            .putExtra("workspaceId", alert.workspaceId)
        val pending = PendingIntent.getActivity(
            context,
            "$connectionId:${alert.workspaceId}".hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, alert.channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alert.title)
            .setContentText("Open Intent to review the workspace.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        manager.notify("$connectionId:${alert.workspaceId}:${alert.subjectId}", 0, notification)
    }
}

internal fun suppressAgentAlert(agentId: String, agents: List<Agent>?, idle: Boolean): Boolean {
    val agent = agents?.firstOrNull { it.id == agentId }
    if (agent?.alertMuted() == true) return true
    return idle && agents.orEmpty().any { !it.alertMuted() && it.holdsCompletion(agentId) }
}

private fun Agent.alertMuted(): Boolean =
    notificationsMuted || metadata?.flag("isBackground") == true
private fun Agent.holdsCompletion(agentId: String): Boolean =
    runningForAlert() || (id == agentId && waitingForAlert())
private fun Agent.runningForAlert(): Boolean =
    isActive || isStreaming || isProcessing || isResponding || turnInFlight || status == "active"
private fun Agent.waitingForAlert(): Boolean =
    isWaitingForOtherAgents || waitingForAgentIds.isNotEmpty()
