package tailcatkit

import (
	"encoding/json"
	"errors"
	"net"
	"strings"
	"sync"
	"tailscale.com/net/netmon"
)

// InterfaceProvider supplies current interfaces through Android's Java APIs.
type InterfaceProvider interface{ InterfacesJSON() (string, error) }

var interfaceProviderOnce sync.Once

// RegisterInterfaceProvider must run before any client is created. The provider
// is queried on each netmon refresh, rather than caching a network snapshot.
func RegisterInterfaceProvider(provider InterfaceProvider) {
	interfaceProviderOnce.Do(func() {
		netmon.RegisterInterfaceGetter(func() ([]netmon.Interface, error) {
			data, err := provider.InterfacesJSON()
			if err != nil {
				return nil, errors.New("Android interface enumeration failed")
			}
			return parseInterfaces(data)
		})
	})
}

type interfaceRecord struct {
	Name  string
	Index int
	MTU   int
	Flags uint
	Addrs []string
}

func parseInterfaces(data string) ([]netmon.Interface, error) {
	var records []interfaceRecord
	if err := json.Unmarshal([]byte(data), &records); err != nil {
		return nil, errors.New("invalid Android interface data")
	}
	interfaces := make([]netmon.Interface, 0, len(records))
	for _, record := range records {
		iface := netmon.Interface{Interface: &net.Interface{Name: record.Name, Index: record.Index, MTU: record.MTU, Flags: net.Flags(record.Flags)}, AltAddrs: make([]net.Addr, 0, len(record.Addrs))}
		for _, addr := range record.Addrs {
			// Java IPv6 host addresses may include a scope name or index.
			parts := strings.SplitN(addr, "/", 2)
			if len(parts) != 2 {
				return nil, errors.New("invalid Android interface prefix")
			}
			host := strings.SplitN(parts[0], "%", 2)[0]
			ip, network, err := net.ParseCIDR(host + "/" + parts[1])
			if err != nil {
				return nil, errors.New("invalid Android interface address")
			}
			network.IP = ip
			iface.AltAddrs = append(iface.AltAddrs, network)
		}
		interfaces = append(interfaces, iface)
	}
	return interfaces, nil
}
