# Terracotta native patches

The bundled `libterracotta.so` files are built from
`https://github.com/burningtnt/Terracotta` at commit
`74364326038f9a33306777a56ed9bb6664799a1d`, with the patches in this
directory applied before the Android builds.

`0001-battly-no-tun-relays.patch` keeps Terracotta Scaffolding entirely in
no-TUN mode and lets the Android client provide Battly relay nodes.
