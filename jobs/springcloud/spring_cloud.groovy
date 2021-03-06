package springcloud

import javaposse.jobdsl.dsl.DslFactory

import org.springframework.jenkins.cloud.ci.ConsulSpringCloudDeployBuildMaker
import org.springframework.jenkins.cloud.ci.CustomJobFactory
import org.springframework.jenkins.cloud.ci.SleuthBenchmarksBuildMaker
import org.springframework.jenkins.cloud.ci.SpringCloudDeployBuildMaker
import org.springframework.jenkins.cloud.ci.SpringCloudDeployBuildMakerBuilder
import org.springframework.jenkins.cloud.ci.SpringCloudKubernetesDeployBuildMaker
import org.springframework.jenkins.cloud.ci.SpringCloudReleaseToolsBuildMaker
import org.springframework.jenkins.cloud.ci.SpringCloudReleaseTrainDocsMaker
import org.springframework.jenkins.cloud.ci.VaultSpringCloudDeployBuildMaker
import org.springframework.jenkins.cloud.common.CloudJdkConfig
import org.springframework.jenkins.cloud.compatibility.BootCompatibilityBuildMaker
import org.springframework.jenkins.cloud.compatibility.ManualBootCompatibilityBuildMaker
import org.springframework.jenkins.cloud.e2e.BreweryEndToEndBuildMaker
import org.springframework.jenkins.cloud.e2e.EndToEndBuildMaker
import org.springframework.jenkins.cloud.e2e.JdkBreweryEndToEndBuildMaker
import org.springframework.jenkins.cloud.e2e.NetflixEndToEndBuildMaker
import org.springframework.jenkins.cloud.e2e.SleuthEndToEndBuildMaker
import org.springframework.jenkins.cloud.e2e.SpringCloudSamplesEndToEndBuildMaker
import org.springframework.jenkins.cloud.e2e.SpringCloudSamplesEndToEndBuilder
import org.springframework.jenkins.cloud.e2e.SpringCloudSamplesTestsBuildMaker
import org.springframework.jenkins.cloud.qa.ConsulSonarBuildMaker
import org.springframework.jenkins.cloud.qa.KubernetesSonarBuildMaker
import org.springframework.jenkins.cloud.qa.SonarBuildMaker
import org.springframework.jenkins.cloud.release.SpringCloudMetaReleaseMaker
import org.springframework.jenkins.cloud.release.SpringCloudMetaReleaseRepoPurger
import org.springframework.jenkins.cloud.release.SpringCloudReleaseMaker
import org.springframework.jenkins.cloud.release.SpringCloudReleaseMasterMaker
import org.springframework.jenkins.cloud.release.SpringCloudReleaserOptions

import static org.springframework.jenkins.cloud.common.AllCloudJobs.ALL_DEFAULT_JOBS
import static org.springframework.jenkins.cloud.common.AllCloudJobs.ALL_JOBS_WITH_TESTS
import static org.springframework.jenkins.cloud.common.AllCloudJobs.ALL_MASTER_RELEASER_JOBS
import static org.springframework.jenkins.cloud.common.AllCloudJobs.ALL_RELEASER_JOBS
import static org.springframework.jenkins.cloud.common.AllCloudJobs.ALL_STREAM_JOBS_FOR_RELEASER
import static org.springframework.jenkins.cloud.common.AllCloudJobs.CUSTOM_BUILD_JOBS
import static org.springframework.jenkins.cloud.common.AllCloudJobs.JOBS_WITHOUT_TESTS
import static org.springframework.jenkins.cloud.common.AllCloudJobs.JOBS_WITH_BRANCHES

DslFactory dsl = this

println "Projects with tests $ALL_JOBS_WITH_TESTS"
println "Projects without tests $JOBS_WITHOUT_TESTS"

// CI BUILDS
// Branch build maker that allows you to build and deploy a branch - this will be done on demand
new SpringCloudDeployBuildMaker(dsl).with { SpringCloudDeployBuildMaker maker ->
	(ALL_DEFAULT_JOBS).each {
		// JDK compatibility
		new SpringCloudDeployBuildMakerBuilder(dsl)
				.prefix("spring-cloud-${jdk11()}").jdkVersion(jdk11())
				.deploy(false).upload(false).build().deploy(it)
		new SpringCloudDeployBuildMakerBuilder(dsl)
				.prefix("spring-cloud-${jdk15()}").jdkVersion(jdk15())
				.onGithubPush(false).cron(oncePerDay())
				.deploy(false).upload(false).build().deploy(it)
		// Normal CI build
		new SpringCloudDeployBuildMakerBuilder(dsl)
				.build().deploy(it)
	}
	JOBS_WITHOUT_TESTS.each {
		// JDK compatibility
		new SpringCloudDeployBuildMakerBuilder(dsl)
				.prefix("spring-cloud-${jdk11()}").jdkVersion(jdk11()).deploy(false)
				.upload(false).build().deployWithoutTests(it)
		new SpringCloudDeployBuildMakerBuilder(dsl)
				.prefix("spring-cloud-${jdk15()}").jdkVersion(jdk15()).onGithubPush(false).cron(oncePerDay()).deploy(false)
				.upload(false).build().deployWithoutTests(it)
		// Normal CI build
		new SpringCloudDeployBuildMakerBuilder(dsl)
				.build().deployWithoutTests(it)
	}
}

// Custom jobs builder
CUSTOM_BUILD_JOBS.each { String projectName ->
	new CloudJdkConfig().with {
		new CustomJobFactory(dsl).deploy(projectName)
		new CustomJobFactory(dsl).jdkVersion(projectName, jdk11())
		new CustomJobFactory(dsl).jdkVersion(projectName, jdk15())
	}
	List<String> branches = JOBS_WITH_BRANCHES[projectName]
	if (branches) {
		branches.each {
			new CustomJobFactory(dsl).deploy(projectName, it)
		}
	}
}

new SpringCloudReleaseToolsBuildMaker(dsl).with {
	deploy()
	deploy("1.0.x")
}

new SpringCloudSamplesTestsBuildMaker(dsl).with {
	buildForIlford()
	[jdk11(), jdk15()].each {
		buildForIlfordWithJdk(it)
	}
}

new SpringCloudReleaseTrainDocsMaker(dsl).with {
	deploy(masterBranch())
	deploy("Hoxton")
}

// BRANCHES BUILD - spring-cloud organization
// Build that allows you to deploy, and build gh-pages of multiple branches. Used for projects
// where we support multiple versions
JOBS_WITH_BRANCHES.each { String project, List<String> branches ->
	if (CUSTOM_BUILD_JOBS.contains(project)) {
		return
	}
	branches.each { String branch ->
		boolean checkTests = !JOBS_WITHOUT_TESTS.contains(project)
		new SpringCloudDeployBuildMaker(dsl).deploy(project, branch, checkTests)
		new BootCompatibilityBuildMaker(dsl).with {
			it.buildWithTests("${project}-${branch}", project, branch, oncePerDay(), checkTests)
		}
	}
}
// Release branches for Spring Cloud Release
new SpringCloudDeployBuildMaker(dsl)
		.deploy('spring-cloud-release', 'Hoxton', false)

new ConsulSpringCloudDeployBuildMaker(dsl).deploy()
new SpringCloudKubernetesDeployBuildMaker(dsl).deploy()
new VaultSpringCloudDeployBuildMaker(dsl).with {
	deploy(masterBranch())
}

// CI BUILDS FOR INCUBATOR
new SpringCloudDeployBuildMaker(dsl, "spring-cloud-incubator").with {
	deploy("spring-cloud-contract-raml")
	deploy("spring-cloud-rsocket")
}

// SLEUTH
new SleuthBenchmarksBuildMaker(dsl).buildSleuth()
new SpringCloudSamplesEndToEndBuildMaker(dsl).with {
	buildWithMavenTests("sleuth-issues", masterBranch(), oncePerDay())
	buildWithMavenTests("sleuth-issues", "2.2.x", oncePerDay())
	buildWithMavenTests("sleuth-documentation-apps", masterBranch(), oncePerDay())
	buildWithMavenTests("sleuth-documentation-apps", "2.2.x", oncePerDay())
}
new SleuthEndToEndBuildMaker(dsl).with {
	buildSleuth(oncePerDay())
}

// CONTRACT
new SpringCloudSamplesEndToEndBuilder().with {
	it.withRepoName("spring-cloud-contract-samples")
	  .withProjectName("spring-cloud-contract-samples-build-only")
	  .withBranchName("3.0.x")
	  .withEnvs(["SKIP_COMPATIBILITY": "true", "SKIP_DOCS" : "true"])
	  .withCronExpr(oncePerDay())
	  .withMavenTests(false)
	  .withGradleTests(false)
}.build(dsl)

new SpringCloudSamplesEndToEndBuilder().with {
	it.withRepoName("spring-cloud-contract-samples")
	  .withProjectName("spring-cloud-contract-samples-compatibility-only")
	  .withBranchName("3.0.x")
	  .withEnvs(["SKIP_BUILD": "true", "SKIP_DOCS" : "true"])
	  .withCronExpr(oncePerDay())
	  .withMavenTests(false)
	  .withGradleTests(false)
}.build(dsl)

new SpringCloudSamplesEndToEndBuilder().with {
	it.withRepoName("spring-cloud-contract-samples")
	  .withProjectName("spring-cloud-contract-samples-docs-only")
	  .withBranchName("3.0.x")
	  .withEnvs(["SKIP_BUILD": "true", "SKIP_COMPATIBILITY" : "true"])
	  .withCronExpr(oncePerDay())
	  .withMavenTests(false)
	  .withGradleTests(false)
}.build(dsl)

new SpringCloudSamplesEndToEndBuilder().with {
	it.withProjectAndRepoName("spring-cloud-contract-samples")
	  .withBranchName("3.0.x")
	  .withEnvs(["SKIP_COMPATIBILITY": "true", "SKIP_DOCS" : "true"])
	  .withCronExpr(oncePerDay())
	  .withJdk(jdk15())
	// for postman <-> swagger
	  .withNodeJs(true)
	  .withMavenTests(false)
	  .withGradleTests(false)
}.build(dsl)

new SpringCloudSamplesEndToEndBuilder().with {
	it.withProjectAndRepoName("spring-cloud-contract-samples")
	  .withBranchName("master")
	  .withCronExpr(oncePerDay())
	// for postman <-> swagger
	  .withEnvs(["SKIP_COMPATIBILITY": "true"])
	  .withNodeJs(true)
	  .withMavenTests(false)
	  .withGradleTests(false)
}.build(dsl)

new SpringCloudSamplesEndToEndBuilder().with {
	it.withProjectAndRepoName("spring-cloud-contract-samples")
	  .withBranchName("master")
	  .withCronExpr(oncePerDay())
	// for postman <-> swagger
	  .withNodeJs(true)
	  .withJdk(jdk11())
	  .withEnvs([SKIP_DOCS: "true", SKIP_COMPATIBILITY: "true"])
	  .withMavenTests(false)
	  .withGradleTests(false)
}.build(dsl)

new SpringCloudSamplesEndToEndBuilder().with {
	it.withProjectAndRepoName("spring-cloud-contract-samples")
	  .withBranchName("master")
	  .withCronExpr(oncePerDay())
	// for postman <-> swagger
	  .withNodeJs(true)
	  .withJdk(jdk15())
	// don't want to check compatibility against Greenwich
	  .withEnvs([SKIP_DOCS: "true", SKIP_COMPATIBILITY: "true"])
	  .withMavenTests(false)
	  .withGradleTests(false)
}.build(dsl)

new SpringCloudSamplesEndToEndBuildMaker(dsl).with {
	buildWithMavenTests("the-legacy-app", masterBranch(), oncePerDay())
	buildWithMavenTests("the-legacy-app", "2.2.x", oncePerDay())
	buildWithMavenTests("sc-contract-car-rental", masterBranch(), oncePerDay())
	buildWithMavenTests("sc-contract-car-rental", "2.2.x", oncePerDay())
}

new SpringCloudSamplesEndToEndBuilder().with {
	it.withRepoName("Pearson-Contracts")
	  .withProjectName("pearson-contracts")
	  .withOrganization("marcingrzejszczak")
	  .withCronExpr(oncePerDay())
	  .withJdk(jdk8())
	  .withMavenTests(true)
	  .withGradleTests(true)
	  .withWipeOutWorkspace(false)
}.build(dsl)

new NetflixEndToEndBuildMaker(dsl).with {
	build(oncePerDay())
}
new JdkBreweryEndToEndBuildMaker(dsl).with { withJdk(jdk11()).build() }
new JdkBreweryEndToEndBuildMaker(dsl).with { withJdk(jdk15()).build() }

// new LatestJdkBreweryEndToEndBuildMaker(dsl).build()
["Hoxton", "2020.0"].each {
	new BreweryEndToEndBuildMaker(dsl).build(it)
}

// E2E
new EndToEndBuildMaker(dsl, "spring-cloud-samples").with {
	buildWithoutTests("eureka-release-train-interop", oncePerDay())
}

// QA
(ALL_JOBS_WITH_TESTS - ["spring-cloud-contract", "spring-cloud-consul", "spring-cloud-vault", "spring-cloud-function", "spring-cloud-kubernetes"]).each {
	new SonarBuildMaker(dsl).buildSonar(it)
	// new MutationBuildMaker(dsl).build(it)
}
new ConsulSonarBuildMaker(dsl).buildSonar()
// new ConsulMutationBuildMaker(dsl).build()
new KubernetesSonarBuildMaker(dsl).buildSonar()
// new MutationBuildMaker(dsl).build("spring-cloud-contract")

// RELEASER
ALL_MASTER_RELEASER_JOBS.each {
	new SpringCloudReleaseMasterMaker(dsl).release(it, SpringCloudReleaserOptions.springCloudMaster())
}
ALL_RELEASER_JOBS.each {
	new SpringCloudReleaseMaker(dsl).release(it, SpringCloudReleaserOptions.springCloud())
}
ALL_STREAM_JOBS_FOR_RELEASER.each {
	new SpringCloudReleaseMaker(dsl).release(it, SpringCloudReleaserOptions.springCloudStream())
}
new SpringCloudMetaReleaseMaker(dsl)
		.release("spring-cloud-meta-releaser", SpringCloudReleaserOptions.springCloud())
new SpringCloudMetaReleaseMaker(dsl)
		.release("spring-cloud-stream-meta-releaser", SpringCloudReleaserOptions.springCloudStream())
new SpringCloudMetaReleaseRepoPurger(dsl).build()

// Compatibility builds
new ManualBootCompatibilityBuildMaker(dsl).build()
