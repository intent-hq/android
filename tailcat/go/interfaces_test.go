package tailcatkit

import (
	"net"
	"tailscale.com/net/netmon"
	"testing"
)

type testInterfaces struct{ data string }

func (p *testInterfaces) InterfacesJSON() (string, error) { return p.data, nil }

func TestInjectedInterfaces(t *testing.T) {
	p := &testInterfaces{`[{"Name":"wlan0","Index":7,"MTU":1500,"Flags":17,"Addrs":["192.0.2.8/24","fe80::1%7/64"]},{"Name":"empty","Index":8,"Addrs":[]}]`}
	RegisterInterfaceProvider(p)
	defer netmon.RegisterInterfaceGetter(nil)
	interfaces, err := netmon.GetInterfaceList()
	if err != nil || len(interfaces) != 2 {
		t.Fatalf("interfaces: %v", err)
	}
	first := interfaces[0]
	if first.Name != "wlan0" || first.Index != 7 || first.MTU != 1500 || first.Flags != net.FlagUp|net.FlagMulticast {
		t.Fatal("metadata mismatch")
	}
	addrs, err := first.Addrs()
	if err != nil || len(addrs) != 2 || addrs[0].String() != "192.0.2.8/24" || addrs[1].String() != "fe80::1/64" {
		t.Fatal("addresses mismatch")
	}
	empty, err := interfaces[1].Addrs()
	if err != nil || len(empty) != 0 {
		t.Fatal("empty addresses fell back to OS")
	}
	p.data = `[]`
	interfaces, err = netmon.GetInterfaceList()
	if err != nil || len(interfaces) != 0 {
		t.Fatal("provider was not refreshed")
	}
	p.data = `[{"Addrs":["invalid"]}]`
	if _, err := netmon.GetInterfaceList(); err == nil {
		t.Fatal("invalid address accepted")
	}
}
