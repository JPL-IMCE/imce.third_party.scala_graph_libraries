# IMCE Third-Party Scala Graph Libraries

[![Build Status](https://travis-ci.org/JPL-IMCE/imce.third_party.scala_graph_libraries.svg?branch=master)](https://travis-ci.org/JPL-IMCE/imce.third_party.scala_graph_libraries)
[ ![Download](https://api.bintray.com/packages/jpl-imce/gov.nasa.jpl.imce/imce.third_party.scala_graph_libraries/images/download.svg) ](https://bintray.com/jpl-imce/gov.nasa.jpl.imce/imce.third_party.scala_graph_libraries/_latestVersion)
 
This project builds an aggregates of several Scala libraries for graph data structures published 
by several organizations:

- [com.assembla.scala-incubator](https://github.com/scala-graph/scala-graph) In-memory graph library intended
  to fit seamlessly into the Scala Collection Library.

- [net.liftweb](https://www.assembla.com/spaces/liftweb/wiki/JSON_Support) The JSON library from the Lift web framework for Scala

## Notes about SBT

As of SBT 0.13.16, there is an incompatibility with Coursier 1.0.0-RC10.
Specifically, `sbt.ConfigurationReport.details` is empty.

To see the difference, add `projects/coursier.sbt` with:

```sbt
// https://github.com/coursier/coursier
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC10")
```

With this file, `universal:mappings` will fail because `sbt.ConfigurationReport.details` is empty.
Without this file, `universal:mappings` works as intended.
