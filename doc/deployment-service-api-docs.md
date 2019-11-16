# Deployment Service

* [Get List](#get-list)
* [Create](#create)
* [Delete](#delete)
* [Get Localization Resources](#get-localization-resources)

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
| deployment-id | path       | The id of the deployment | Yes      |

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

## Get Localization Resources
Get localization resources in accordance with user preferences.

### `GET /api/deployment/localization-resource`

### Request Parameters:

| Name                  | Type       | Description                                                                                           | Required |
| --------------------- | ---------- | ----------------------------------------------------------------------------------------------------- | -------- |
| process-definition-id | query      | The id of process definition which has the resources. Not required, if 'case-definition-id' is passed | No       |
| case-definition-id    | query      | The id of case definition which has the resources. Not required, if 'process-definition-id' is passed | No       |
| Accept-Language       | header     | User preferences of languages                                                                         | Yes      |

**Note:** `process-definition-id` and `case-definition-id` are required despite being marked as `not required`.
It is because the service expects one of them to be passed, but it is impossible to use disjunction in Bean Validation 2.0.
Hence either `process-definition-id` or `case-definition-id` have to be passed. If both are passed, `process-definition-id` takes
a precedence over `case-definition-id`.

### Result:
A JSON object which includes properties from corresponding localization resource bundle.

### Response Codes:
| Code | Media Type | Description |
| ---- | ---------- | ----------- |
| 200  | application/json | Request successful |

### Example:

#### Request:
`GET -H "Accept-Language: ru,en;q=0.9,en-US;q=0.8" /api/deployment/localization-resource?process-definition-id=some-id`

#### Response:
Status 200 OK.
```json
{
  "some_property1": "some_value_1",
  "some_property2": "some_value_2"
}
```

[Deployment Object description]: https://docs.camunda.org/manual/7.10/reference/rest/deployment/post-deployment/#result