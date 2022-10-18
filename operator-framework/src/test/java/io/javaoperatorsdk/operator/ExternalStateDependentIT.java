package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.externalstatedependent.ExternalStateDependentCustomResource;
import io.javaoperatorsdk.operator.sample.externalstatedependent.ExternalStateDependentReconciler;
import io.javaoperatorsdk.operator.sample.externalstatedependent.ExternalStateSpec;
import io.javaoperatorsdk.operator.support.ExternalIDGenServiceMock;

import static io.javaoperatorsdk.operator.sample.externalstate.ExternalStateReconciler.ID_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ExternalStateDependentIT {

  private static final String TEST_RESOURCE_NAME = "test1";

  public static final String INITIAL_TEST_DATA = "initialTestData";
  public static final String UPDATED_DATA = "updatedData";

  private ExternalIDGenServiceMock externalService = ExternalIDGenServiceMock.getInstance();

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(ExternalStateDependentReconciler.class)
          .build();

  // TODO unify the two ITs
  @Test
  void reconcilesResourceWithPersistentState() {
    var resource = operator.create(testResource());
    assertResources(resource, INITIAL_TEST_DATA);

    resource.getSpec().setData(UPDATED_DATA);
    operator.replace(resource);
    assertResources(resource, UPDATED_DATA);

    operator.delete(resource);
    assertResourcesDeleted(resource);
  }

  private void assertResourcesDeleted(ExternalStateDependentCustomResource resource) {
    await().untilAsserted(() -> {
      var cm = operator.get(ConfigMap.class, resource.getMetadata().getName());
      var resources = externalService.listResources();
      assertThat(cm).isNull();
      assertThat(resources).isEmpty();
    });
  }

  private void assertResources(ExternalStateDependentCustomResource resource,
      String initialTestData) {
    await().pollInterval(Duration.ofMillis(700)).untilAsserted(() -> {
      var cm = operator.get(ConfigMap.class, resource.getMetadata().getName());
      var resources = externalService.listResources();
      assertThat(resources).hasSize(1);
      var extRes = externalService.listResources().get(0);
      assertThat(extRes.getData()).isEqualTo(initialTestData);
      assertThat(cm).isNotNull();
      assertThat(cm.getData().get(ID_KEY)).isEqualTo(extRes.getId());
    });
  }

  private ExternalStateDependentCustomResource testResource() {
    var res = new ExternalStateDependentCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName(TEST_RESOURCE_NAME)
        .build());

    res.setSpec(new ExternalStateSpec());
    res.getSpec().setData(INITIAL_TEST_DATA);
    return res;
  }
}
