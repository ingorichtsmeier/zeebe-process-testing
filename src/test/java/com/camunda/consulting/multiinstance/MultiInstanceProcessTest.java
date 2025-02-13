package com.camunda.consulting.multiinstance;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import static io.camunda.zeebe.process.test.assertions.BpmnAssert.*;
import io.camunda.zeebe.process.test.extension.ZeebeProcessTest;

@ZeebeProcessTest
public class MultiInstanceProcessTest {

  private static final Duration TWO_SECONDS = Duration.ofSeconds(2);
  private static final String PROC_DEF_ID = "DynamicMultiInstance";
  private ZeebeTestEngine engine;
  private ZeebeClient client;

  private static final Logger LOG = LoggerFactory.getLogger(MultiInstanceProcessTest.class);

  @Test
  public void testHappyPath() throws InterruptedException, TimeoutException {
    DeploymentEvent delopyment = client.newDeployResourceCommand()
        .addResourceFromClasspath("dynamic-multiinstance.bpmn").send().join();
    assertThat(delopyment).containsProcessesByBpmnProcessId(PROC_DEF_ID);

    ProcessInstanceEvent processInstance =
        client.newCreateInstanceCommand().bpmnProcessId(PROC_DEF_ID).latestVersion()
            .variable("dynamicCollection", List.of(3, 5)).send().join();

    engine.waitForIdleState(TWO_SECONDS);
    assertThat(processInstance).isWaitingAtElements("DoItMoreThanOnceServiceTask");
    
    ActivatedJob activatedJob = client.newActivateJobsCommand().jobType("setDynamic")
        .maxJobsToActivate(1).send().join().getJobs().get(0);
    LOG.info("variables: {}", activatedJob.getVariablesAsMap());
    assertThat(activatedJob).hasElementId("DoItMoreThanOnceServiceTask");
    client.newCompleteCommand(activatedJob).variable("listener", 12).send().join();
    
    engine.waitForIdleState(TWO_SECONDS);
    activatedJob = client.newActivateJobsCommand().jobType("setDynamic").maxJobsToActivate(1).send().join().getJobs().get(0);
    LOG.info("variables: {}", activatedJob.getVariablesAsMap());
    client.newCompleteCommand(activatedJob).variable("listener", 13).send().join();
    
    engine.waitForIdleState(TWO_SECONDS);
    activatedJob = client.newActivateJobsCommand().jobType("singleInstanceJob").maxJobsToActivate(1).send().join().getJobs().get(0);
    LOG.info("Variables in singleInstanceJob: {}", activatedJob.getVariablesAsMap());
    client.newCompleteCommand(activatedJob).variable("jobVar", 13).send().join();
    
    engine.waitForIdleState(TWO_SECONDS);
    activatedJob = client.newActivateJobsCommand().jobType("singleInstanceJob").maxJobsToActivate(1).send().join().getJobs().get(0);
    LOG.info("Variables in singleInstanceJob: {}", activatedJob.getVariablesAsMap());
    client.newCompleteCommand(activatedJob).variable("jobVar", 14).send().join();
    
    engine.waitForIdleState(TWO_SECONDS);
    assertThat(processInstance).isCompleted();
  }
  
  @Test
  public void testComplexMultiInstance() {
    DeploymentEvent deployment = client.newDeployResourceCommand().addResourceFromClasspath("complex-dynamic-multiinstance.bpmn").send().join();
    assertThat(deployment).containsProcessesByBpmnProcessId("ComplexMultiinstance");
    
  }

}
