package com.termux.terminal;

/**
 * The interface for communication between the terminal transport and its client. It is used to
 * send callbacks to the client when the terminal transport changes or for sending other
 * back data to the client like logs.
 */
public interface TerminalSessionClient {








    void onTerminalCursorStateChange(boolean state);



    Integer getTerminalCursorStyle();



    void logError(String tag, String message);

    void logWarn(String tag, String message);

    void logInfo(String tag, String message);

    void logDebug(String tag, String message);

    void logVerbose(String tag, String message);

    void logStackTraceWithMessage(String tag, String message, Exception e);

    void logStackTrace(String tag, Exception e);

}
