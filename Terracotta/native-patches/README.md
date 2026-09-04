# Terracotta native patches

The bundled `libterracotta.so` files are built from
`https://github.com/burningtnt/Terracotta` at commit
`74364326038f9a33306777a56ed9bb6664799a1d`, with the patches in this
directory applied before the Android builds.

`0001-battly-no-tun-relays.patch` keeps Terracotta Scaffolding entirely in
no-TUN mode, lets the Android client provide Battly relay nodes, and replaces
Terracotta's hard-coded Chinese LAN lobby MOTD with a BattlyWorlds label.

`0002-battly-relay-fallback.patch` keeps direct P2P as the preferred route but
allows the configured Battly nodes to relay traffic when a router isolates
clients or a direct connection cannot be established. This is required for
devices sharing a public IP and for restrictive mobile and Wi-Fi networks. It
also pins the EasyTier revision used for the bundled binaries so rebuilding the
same Terracotta commit cannot silently pull a different transport implementation.
