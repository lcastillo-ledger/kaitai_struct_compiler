# Working Flow Commands

Compile and publish locally

`sbt compilerJVM/debian:packageBin`
`sudo dpkg -i jvm/target/kaitai-struct-compiler_0.10-SNAPSHOT_all.deb`

Generate pdf and svg files for file `my_format.ksy`

`ksc -t asciidoc my_format.ksy` 
`asciidoctor-pdf my_format.adoc`
`ksc -t graphviz my_format.ksy` 
`dot -O -Tsvg my_format.dot` 
