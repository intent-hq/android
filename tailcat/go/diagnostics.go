package tailcatkit

// PingError returns Go error text directly, avoiding gomobile proxy exceptions.
// An empty string means success. Callers must redact credentials before logging.
func (c *Client) PingError(timeoutMs int) string {
	return errorText(c.Ping(timeoutMs))
}

// DialResult keeps the connection and error text in a single bridge-safe result.
type DialResult struct {
	Conn      *Conn
	ErrorText string
}

// DialWithError preserves the native error text when a TCP dial fails.
func (c *Client) DialWithError(port, timeoutMs int) *DialResult {
	conn, err := c.Dial(port, timeoutMs)
	return &DialResult{Conn: conn, ErrorText: errorText(err)}
}

func errorText(err error) string {
	if err == nil {
		return ""
	}
	return err.Error()
}
