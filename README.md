Visual Source Safe - Jenkins plugin
===================================

[Jenkins](https://wiki.jenkins-ci.org/display/JENKINS/Visual+SourceSafe+Plugin) vss plugin

Created this fork primarily with the intention of fixing the polling bug. JENKINS-10730

In doing so, I tried upgrading the version of com4j which appears to work. I use this now at a client's
site and have not had to restart Jenkins due to a memory leak.

Building
========
Install [maven](http://maven.apache.org)

then in root of these files type:

    mvn package

You should end up with `vss.hpi` inside the `target` directory which you can install in Jenkins as if
you had downloaded it manually.

I don't know how we go about getting this officially released as version 1.10? I don't want to be the
maintainer as I'm only using VSS for a particular client and _hopefully_ won't be using it in the
longer term.

