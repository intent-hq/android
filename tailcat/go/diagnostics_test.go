package tailcatkit

import (
	"errors"
	"testing"
)

func TestErrorText(t *testing.T) {
	if got := errorText(nil); got != "" {
		t.Fatal(got)
	}
	if got := errorText(errors.New("native detail")); got != "native detail" {
		t.Fatal(got)
	}
}

func TestNativeErrors(t *testing.T) {
	c := NewClient("invalid")
	defer c.Close()
	if got := c.PingError(10); got != `server address doesn't start with "tc"` {
		t.Fatal(got)
	}
	result := c.DialWithError(0, 10)
	if result.Conn != nil || result.ErrorText != "tailcatkit: invalid port 0" {
		t.Fatal(result.ErrorText)
	}
}
