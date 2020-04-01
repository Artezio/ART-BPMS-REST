# Deployment Service

* [Get List](#get-list)
* [Create](#create)
* [Delete](#delete)
* [List Public Resources](#list-public-resources)
* [Get Public Resource](#get-public-resource)

## Get List
Get a list of all deployments.

### `GET /api/deployment` 

### Result:
A JSON array of deployment objects. For information about deployment object see [Deployment Object description].

### Response Codes:
| Code | Media Type       | Description             |
| ---- | ---------------- | ----------------------  |
| 200  | application/json | Request successful      |

### Example:

#### Request:
`GET /api/deployment`
    
#### Response:
```json
[
  {
    "id": "someId",
    "name": "deploymentName",
    "source": "process application",
    "tenantId": null,
    "deploymentTime": "2013-04-23T13:42:43.000+0200"
  }
]
```

## Create
Create a deployment with specified resources.

### `POST /api/deployment/create`

### Request Parameters:

| Name            | Type       | Description             | Required |
| --------------- | ---------- | ----------------------- | -------- |
| deployment-name | query      | Name for the deployment | Yes      |

### Request Body:
| Media Type          | Description                                    |
| ------------------- | ---------------------------------------------- |
| multipart/form-data | Resources which the deployment will consist of |

### Result:
A JSON object representing created deployment object. For information about deployment object see [Deployment Object description].

### Response Codes:
| Code | Media Type       | Description                               |
| ---- | ---------------- | ----------------------------------------- |
| 200  | application/json | Request successful |
| 403  |                  | The user is not allowed to create deployments |

### Example:

#### Request:
`POST /deployment/create`

```http request
--28319d96a8c54b529aa9159ad75edef9
Content-Disposition: form-data; name="deployment-name"

aName
--28319d96a8c54b529aa9159ad75edef9
Content-Disposition: form-data; name="enable-duplicate-filtering"

true
--28319d96a8c54b529aa9159ad75edef9
Content-Disposition: form-data; name="deployment-source"

process application
--28319d96a8c54b529aa9159ad75edef9
Content-Disposition: form-data; name="data"; filename="test.bpmn"

<?xml version="1.0" encoding="UTF-8"?>
<bpmn2:definitions ...>
  <!-- BPMN 2.0 XML omitted -->
</bpmn2:definitions>
--28319d96a8c54b529aa9159ad75edef9--
```

#### Response:
Status 200 OK.

```json
{
    "links": [],
    "id": "aDeploymentId",
    "name": "aName",
    "source": "process application",
    "deploymentTime": "2013-01-23T13:59:43.000+0200",
    "tenantId": null,
    "deployedProcessDefinitions": {
        "aProcDefId": {
            "id": "aProcDefId",
            "key": "aKey",
            "category": "aCategory",
            "description": "aDescription",
            "name": "aName",
            "version": 42,
            "resource": "aResourceName",
            "deploymentId": "aDeploymentId",
            "diagram": "aResourceName.png",
            "suspended": true,
            "tenantId": null,
            "versionTag": null
        }
    },
    "deployedCaseDefinitions": null,
    "deployedDecisionDefinitions": null,
    "deployedDecisionRequirementsDefinitions": null
}
```

## Delete
Delete a deployment.

### `DELETE /api/deployment/{deployment-id}`

### Request Parameters:
| Name          | Type       | Description              | Required |
| ------------- | ---------- | ------------------------ | -------- |
| deployment-id | path       | The id of a deployment | Yes      |

### Result:
This method returns no content.

### Response Codes:

| Code | Description |
| ---- | ----------- |
| 204  | Request successful |
| 403  | User is not allowed to delete deployments |

### Example:

#### Request:
`DELETE /api/deployment/some-id`

### Response:
Status 204 No Content.

## List Public Resources

### `GET /api/deployment/public-resources`

### Request Parameters:
| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| processDefinitionId | String | The id of a process definition | *true |
| caseDefinitionId | String | The id of a case definition | *true |
| formKey | String |  | true |

*\* - Params are mutually exclusive. If one is passed, another is not necessary.*

### Result:
A JSON in HAL format that contains links to download public resources.
 
### Response Codes:

| Code | Description |
| ---- | ----------- |
| 200  | Request successful |

### Example:

#### Request:
`GET api/deployment/public-resources?process-definition-id=someProcessDefinitionId&formKey=someFormKey`

#### Response:
```json
{
  "_links": {
    "resourcesBaseUrl": {
      "href": "http://localhost:8080/bpms-rest/api/deployment/public-resource/embedded:deployment:/1234567890/"
    },
    "items": [
      {
        "href": "http://localhost:8080/bpms-rest/api/deployment/public-resource/embedded:deployment:/1234567890/custom-components/component.js",
        "name": "custom-components/component.js"
      }
    ]
  }
}
```

## Get Public Resource

`GET api/deployment/public-resource/{deployment-protocol}/{deployment-id}/{resource-key: .*}`

### Request Parameters:

| Name | Type | Description | Required |
| ---- | ---- | ----------- | -------- |
| deploymentProtocol | String | Deployment protocol of the requested resource ('embedded:app:' or 'embedded:deployment:'). To get more about protocols see [Camunda Embedded Task Forms] | true |
| deploymentId | String | The id of a deployment | true |
| resourceKey | List | The requested resource path. No deployment protocol is needed. The path depends on used [Resource Loader]. If default resource loaders are used, the path is relative to the root of either deployment or webapp resources | true |

### Result:

Requested resource in a binary format.

### Response Codes:

| Code | Description |
| ---- | ----------- |
| 200  | Request successful |
| 404  | Resource is not found |

### Example:

#### Request:

`GET http://localhost:8080/bpms-rest/api/deployment/public-resource/embedded:deployment:/1234567890/resource-folder/resource.txt`

#### Response:
Status 200 OK.

```http request
200 OK
Content-Type: text/plain; charset=utf-8
Content-Disposition: attachment; filename="resource.ext"
Content-Length: ...

[skipped content]
```

[Camunda Embedded Task Forms]: https://docs.camunda.org/manual/7.10/user-guide/task-forms/#embedded-task-forms
[Deployment Object description]: https://docs.camunda.org/manual/7.10/reference/rest/deployment/post-deployment/#result
[Resource Loader]: ../README.MD#resource-loaders