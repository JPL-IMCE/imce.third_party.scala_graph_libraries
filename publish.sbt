
// do not include all repositories in the POM
// (this is important for staging since artifacts published to a staging repository
//  can be promoted (i.e. published) to another repository)
pomAllRepositories := false

// make sure no repositories show up in the POM file
pomIncludeRepository := { _ => false }

// include *.zip artifacts in the POM dependency section
makePomConfiguration :=
  makePomConfiguration.value.copy(includeTypes = Set(Artifact.DefaultType, Artifact.PomType, "zip"))

// publish to bintray.com via: `sbt publish`
publishTo := Some(
  "JPL-IMCE" at
    s"https://api.bintray.com/content/jpl-imce/${organization.value}/${moduleName.value}/${version.value}")

PgpKeys.useGpg := true

PgpKeys.useGpgAgent := true

pgpSecretRing := file("local.secring.gpg")

pgpPublicRing := file("local.pubring.gpg")

git.baseVersion := Versions.version

versionWithGit
