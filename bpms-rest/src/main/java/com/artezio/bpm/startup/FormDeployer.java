package com.artezio.bpm.startup;

import com.artezio.bpm.services.DeploymentSvc;
import com.artezio.forms.FormClient;

import javax.annotation.PostConstruct;
import javax.ejb.DependsOn;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import java.util.List;

@Singleton
@Startup
@DependsOn("DefaultEjbProcessApplication")
public class FormDeployer {

    @Inject
    private DeploymentSvc deploymentSvc;
    @Inject
    private FormClient formioClient;

    @PostConstruct
    public void uploadForms() {
        if (deploymentSvc.deploymentsExist()) {
            List<String> formIds = deploymentSvc.listLatestDeploymentFormIds();
            formIds.stream()
                    .map(formId -> formId.endsWith(".json") ? formId.substring(0, formId.length() - 5) : formId)
                    .forEach(formId ->
                            formioClient.uploadForm(deploymentSvc.getLatestDeploymentForm(formId)));
        }
    }

}
