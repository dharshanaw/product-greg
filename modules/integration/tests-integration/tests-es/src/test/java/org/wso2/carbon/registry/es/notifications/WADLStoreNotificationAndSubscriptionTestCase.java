/*
 *  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.carbon.registry.es.notifications;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.wink.client.ClientResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.automation.engine.frameworkutils.FrameworkPathUtil;
import org.wso2.carbon.governance.custom.lifecycles.checklist.stub.CustomLifecyclesChecklistAdminServiceExceptionException;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.es.utils.GregESTestBaseTest;
import org.wso2.greg.integration.common.clients.LifeCycleAdminServiceClient;
import org.wso2.greg.integration.common.utils.GenericRestClient;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * This class testes subscription & notification for wadls on store notification
 */
public class WADLStoreNotificationAndSubscriptionTestCase extends GregESTestBaseTest {

    private static final Log log = LogFactory.getLog(WADLStoreNotificationAndSubscriptionTestCase.class);
    private TestUserMode userMode;
    String jSessionIdPublisher;
    String jSessionIdStore;
    String assetId;
    String cookieHeaderPublisher;
    String cookieHeaderStore;
    GenericRestClient genericRestClient;
    Map<String, String> queryParamMap;
    Map<String, String> headerMap;
    String publisherUrl;
    String storeUrl;
    String resourcePath;
    LifeCycleAdminServiceClient lifeCycleAdminServiceClient;
    String lifeCycleName;
    String assetName;
    String path;
    Map<String, String> assocUUIDMap;
    private static final String STATE_CHANGE_MESSAGE = " State changed successfully to Testing!";

    @Factory(dataProvider = "userModeProvider")
    public WADLStoreNotificationAndSubscriptionTestCase(TestUserMode userMode) {
        this.userMode = userMode;
    }

    @BeforeClass(alwaysRun = true)
    public void init() throws Exception {
        super.init(userMode);
        genericRestClient = new GenericRestClient();
        queryParamMap = new HashMap<String, String>();
        headerMap = new HashMap<String, String>();
        resourcePath =
                FrameworkPathUtil.getSystemResourceLocation() + "artifacts" + File.separator + "GREG" + File.separator;
        publisherUrl = automationContext.getContextUrls().getSecureServiceUrl().replace("services", "publisher/apis");
        storeUrl = automationContext.getContextUrls().getSecureServiceUrl().replace("services", "store/apis");
        //need lifeCycleAdminServiceClient to attach a lifecycle to the WADL, as WADLs does not come with
        //a default lifecycle attached
        lifeCycleAdminServiceClient = new LifeCycleAdminServiceClient(backendURL, sessionCookie);
        lifeCycleName = "ServiceLifeCycle";
        setTestEnvironment();
    }

    @Test(groups = {"wso2.greg", "wso2.greg.es"}, description = "create a wadl with a LC attached.")
    public void createWADLAssetWithLC()
            throws JSONException, InterruptedException, IOException,
                   CustomLifecyclesChecklistAdminServiceExceptionException {
        queryParamMap.put("type", "wadl");
        String wadlTemplate = readFile(resourcePath + "json" + File.separator + "wadl-sample.json");
        assetName = "StorageService.wadl";
        String dataBody = String.format(wadlTemplate,
                                        "https://raw.githubusercontent.com/wso2/wso2-qa-artifacts/master/automation-artifacts/greg/wadl/StorageService.wadl",
                                        assetName,
                                        "2.0.0");
        ClientResponse response =
                genericRestClient.geneticRestRequestPost(publisherUrl + "/assets",
                                                         MediaType.APPLICATION_JSON,
                                                         MediaType.APPLICATION_JSON, dataBody
                        , queryParamMap, headerMap, cookieHeaderPublisher);
        JSONObject obj = new JSONObject(response.getEntity(String.class));
        Assert.assertTrue((response.getStatusCode() == 201),
                          "Wrong status code ,Expected 201 Created ,Received " +
                          response.getStatusCode());
        String resultName = obj.get("overview_name").toString();
        Assert.assertEquals(resultName, assetName);
        searchWadlAsset();
        //attach a LC to the wadl
        lifeCycleAdminServiceClient.addAspect(path, lifeCycleName);
        Assert.assertNotNull(assetId, "Empty asset resource id available" +
                                      response.getEntity(String.class));
        Assert.assertTrue(this.getAsset(assetId, "wadl").get("lifecycle")
                                  .equals(lifeCycleName), "LifeCycle not assigned to given asset");
    }

    @Test(groups = {"wso2.greg", "wso2.greg.es"}, description = "Adding subscription to a wadl LC state change",
          dependsOnMethods = {"createWADLAssetWithLC"})
    public void addSubscriptionForLCStateChange() throws JSONException, IOException {

        JSONObject dataObject = new JSONObject();

        dataObject.put("notificationType", "StoreLifeCycleStateChanged");
        dataObject.put("notificationMethod", "work");

        ClientResponse response =
                genericRestClient.geneticRestRequestPost(storeUrl + "/subscription/wadl/" + assetId, MediaType.APPLICATION_JSON,
                                                         MediaType.APPLICATION_JSON, dataObject.toString()
                        , queryParamMap, headerMap, cookieHeaderStore);

        assertTrue((response.getStatusCode() == Response.Status.OK.getStatusCode()),
                   "Wrong status code ,Expected" + Response.Status.OK.getStatusCode() + "Created ,Received " +
                   response.getStatusCode());
    }

    @Test(groups = {"wso2.greg", "wso2.greg.es"}, description = "Change LC state on WADL",
          dependsOnMethods = {"addSubscriptionForLCStateChange" })
    public void changeLCStateWADL() throws JSONException, IOException {
        ClientResponse response =
                genericRestClient.geneticRestRequestPost(publisherUrl + "/assets/" + assetId + "/state",
                                                         MediaType.APPLICATION_FORM_URLENCODED,
                                                         MediaType.APPLICATION_JSON,
                                                         "nextState=Testing&comment=Completed"
                        , queryParamMap, headerMap, cookieHeaderPublisher);
        JSONObject obj = new JSONObject(response.getEntity(String.class));
        String status = obj.get("status").toString();
        Assert.assertEquals(status, STATE_CHANGE_MESSAGE);
    }

    @Test(groups = { "wso2.greg",
                     "wso2.greg.es" }, description = "Adding wrong subscription method to check the error message",
          dependsOnMethods = {"changeLCStateWADL" })
    public void addWrongSubscriptionMethod() throws JSONException, IOException {

        JSONObject dataObject = new JSONObject();
        dataObject.put("notificationType", "StoreResourceUpdated");
        dataObject.put("notificationMethod", "test");

        ClientResponse response = genericRestClient
                .geneticRestRequestPost(storeUrl + "/subscription/wadl/" + assetId, MediaType.APPLICATION_JSON,
                                        MediaType.APPLICATION_JSON, dataObject.toString(), queryParamMap, headerMap, cookieHeaderStore);
        String payLoad = response.getEntity(String.class);
        JSONObject obj = new JSONObject(payLoad);
        assertNotNull(obj.get("error").toString(),
                      "Error message is not contained in the response for wrong notification method \"test\"" + response
                              .getEntity(String.class));
    }

    private void setTestEnvironment() throws JSONException, IOException, XPathExpressionException {
        // Authenticate Publisher
        ClientResponse response = authenticate(publisherUrl, genericRestClient,
                                               automationContext.getSuperTenant().getTenantAdmin().getUserName(),
                                               automationContext.getSuperTenant().getTenantAdmin().getPassword());
        JSONObject obj = new JSONObject(response.getEntity(String.class));
        jSessionIdPublisher = obj.getJSONObject("data").getString("sessionId");
        cookieHeaderPublisher = "JSESSIONID=" + jSessionIdPublisher;

        // Authenticate Store
        ClientResponse responseStore = authenticate(storeUrl, genericRestClient,
                                                    automationContext.getSuperTenant().getTenantAdmin().getUserName(),
                                                    automationContext.getSuperTenant().getTenantAdmin().getPassword());
        obj = new JSONObject(responseStore.getEntity(String.class));
        jSessionIdStore = obj.getJSONObject("data").getString("sessionId");
        cookieHeaderStore = "JSESSIONID=" + jSessionIdStore;

    }

    /**
     * This method get all the wadls in publisher and select the one created by createWADLAssetWithLC method.
     *
     * @throws JSONException
     */
    public void searchWadlAsset() throws JSONException {
        Map<String, String> queryParamMap = new HashMap<>();
        queryParamMap.put("type", "wadl");
        ClientResponse clientResponse = searchAssetByQuery(publisherUrl,genericRestClient,cookieHeaderPublisher,queryParamMap);
        JSONObject obj = new JSONObject(clientResponse.getEntity(String.class));
        JSONArray jsonArray = obj.getJSONArray("list");
        for (int i = 0; i < jsonArray.length(); i++) {
            String name = (String) jsonArray.getJSONObject(i).get("name");
            if (assetName.equals(name)) {
                assetId = (String) jsonArray.getJSONObject(i).get("id");
                path = (String) jsonArray.getJSONObject(i).get("path");
                break;
            }
        }
    }

    private JSONObject getAsset(String assetId, String assetType) throws JSONException {
        Map<String, String> assetTypeParamMap = new HashMap<String, String>();
        assetTypeParamMap.put("type", assetType);
        ClientResponse clientResponse = getAssetById(publisherUrl,genericRestClient,cookieHeaderPublisher,assetId, queryParamMap);
        return new JSONObject(clientResponse.getEntity(String.class));
    }

    @AfterClass(alwaysRun = true)
    public void cleanUp() throws RegistryException, JSONException {
        Map<String, String> queryParamMap = new HashMap<>();
        queryParamMap.put("type", "wadl");
        assocUUIDMap = getAssociationsFromPages(publisherUrl,genericRestClient,cookieHeaderPublisher,assetId, queryParamMap);
        deleteAssetById(publisherUrl,genericRestClient,cookieHeaderPublisher,assetId, queryParamMap);
        deleteAllAssociationsById(publisherUrl,genericRestClient,cookieHeaderPublisher,assetId, queryParamMap);
        queryParamMap.clear();
        for (String uuid : assocUUIDMap.keySet()) {
            queryParamMap.put("type", getType(assocUUIDMap.get(uuid)));
            deleteAssetById(publisherUrl,genericRestClient,cookieHeaderPublisher,uuid, queryParamMap);
        }
    }

    @DataProvider
    private static TestUserMode[][] userModeProvider() {
        return new TestUserMode[][]{
                new TestUserMode[]{TestUserMode.SUPER_TENANT_ADMIN}
                //                new TestUserMode[]{TestUserMode.TENANT_USER},
        };
    }
}
