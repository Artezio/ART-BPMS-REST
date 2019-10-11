# Task Service

* [Complete](#complete)
* [Claim](#claim)
* [Load Form](#load-form)
* [Get Assigned](#get-assigned)
* [Get Available](#get-available)
* [Download File](#download-file)

## Complete
Complete a task. Before completing the task all variables passed to the method will pass "clean up and validation" procedure.
Due to the procedure some variables might not get into the process. If some variable has two or more files with identical names,
the only one of these files will be used and it is not determined which one will be chosen.

### `POST /api/task/{task-id}/complete`

### Parameters:
| Name | Type | Description | Required |
| ---- | ---------- | ----------- | -------- |
| task-id | path | The id of the task to be completed | Yes |

### Result:
A JSON object representing the next task assigned to a user who made this request, if any. For information about properties
see [Task Object description].

### Response Codes:
| Code | Description |
| ---- | ----------- |
| 200 | Request successful |
| 204 | There is no tasks assigned to the user |
| 403 | The user is not allowed to complete the task |

### Example:

#### Request:
`POST /task/anId/complete`<p/>
Request Body:
```json
{
  "variables":
    {
      "aVariable": {"value": "aStringValue"},
      "anotherVariable": {"value": 42},
      "aThirdVariable": {"value": true}
    }
}
```

#### Response:
Status 200 OK. 
```json
{
  "id":"anId",
  "name":"aName",
  "assignee":"anAssignee",
  "created":"2013-01-23T13:42:42.000+0200",
  "due":"2013-01-23T13:49:42.576+0200",
  "followUp":"2013-01-23T13:44:42.437+0200",
  "delegationState":"RESOLVED",
  "description":"aDescription",
  "executionId":"anExecution",
  "owner":"anOwner",
  "parentTaskId":"aParentId",
  "priority":42,
  "processDefinitionId":"aProcDefId",
  "processInstanceId":"aProcInstId",
  "caseDefinitionId":"aCaseDefId",
  "caseInstanceId":"aCaseInstId",
  "caseExecutionId":"aCaseExecution",
  "taskDefinitionKey":"aTaskDefinitionKey",
  "suspended": false,
  "formKey":"aFormKey",
  "tenantId":"aTenantId"
}
```

## Claim
Claims a task for the user doing this request.

### `POST /api/task/{task-id}/claim`

### Parameters:
| Name | Type | Description | Required |
| ---- | ---------- | ----------- | -------- |
| task-id | path | The id of the task | Yes |

### Result:
This method returns no content.

### Response Codes:
| Code | Description |
| ---- | ----------- |
| 204 | Request successful |
| 403 | The user doesn't have an access to assign the task |

### Example:

#### Request:
`POST /task/anId/claim`

#### Response:
Status 204. No content.

## Load Form
Load the form for a task.

### `GET /api/task/{task-id}/form`

### Parameters:
| Name | Located in | Description | Required |
| ---- | ---------- | ----------- | -------- |
| task-id | path | The id of the task which form is requested for | Yes |

### Result:
A JSON object representing request form definition. For detailed information about form definition fields see corresponding
documentation.

### Response Codes:

| Code | Media Type | Description |
| ---- | ---- | ----------- |
| 200 | application/json | Request successful |
| 403 | | The deployed form cannot be retrieved due to missing permissions on task resource |
| 404 | | No deployed form for a given task exists |

### Example:

#### Request:
Request to Formio server: `GET /task/taskId/form`

#### Response:
Status 200 OK.
```json
{
    "_id": "anId",
    "type": "aType",
    "tags": [
        ...
    ],
    "owner": "anOwner",
    "components": [ ... ],
    "access": [
        {
            "roles": [ ... ],
            "type": "aType"
        }
    ],
    "created": "2019-06-28T15:00:41.690Z",
    "display": "aDisplay",
    "title": "aTitle",
    "machineName": "aMachineName",
    "path": "aPath",
    "submissionAccess": [...],
    "name": "aName",
    "modified": "2019-10-07T13:53:29.519Z"
}
```

## Get Assigned
Get the tasks assigned to the user who has done this request.

### `GET /api/task/assigned`

### Result:
A JSON array of task objects. For information about task object see [Task Object description].

### Response Codes:
| Code | Description |
| ---- | ----------- |
| 200  | Request successful |

### Example:

#### Request:
`GET /task/assigned`

#### Response:
```json
[
    {
        "id":"anId",
         "name":"aName",
         "assignee":"anAssignee",
         "created":"2013-01-23T13:42:42.657+0200",
         "due":"2013-01-23T13:49:42.323+0200",
         "followUp:":"2013-01-23T13:44:42.987+0200",
         "delegationState":"RESOLVED",
         "description":"aDescription",
         "executionId":"anExecution",
         "owner":"anOwner",
         "parentTaskId":"aParentId",
         "priority":42,
         "processDefinitionId":"aProcDefId",
         "processInstanceId":"aProcInstId",
         "caseDefinitionId":"aCaseDefId",
         "caseInstanceId":"aCaseInstId",
         "caseExecutionId":"aCaseExecution",
         "taskDefinitionKey":"aTaskDefinitionKey",
         "suspended": false,
         "formKey":"aFormKey",
         "tenantId": "aTenantId"
    }
]
```

## Get Available
Get tasks available to the user who has done this request.

### `GET /api/task/available`

### Result:
A JSON array of task objects. For information about task object see [Task Object description].

### Response Codes:
| Code | Media Type | Description |
| ---- | ---------- | ----------- |
| 200  | application/json | Request successful |

### Example:

#### Request:
`GET /task/available`

#### Response:
```json
[
    {
        "id":"anId",
         "name":"aName",
         "assignee":null,
         "created":"2013-01-23T13:42:42.657+0200",
         "due":"2013-01-23T13:49:42.323+0200",
         "followUp:":"2013-01-23T13:44:42.987+0200",
         "delegationState":"RESOLVED",
         "description":"aDescription",
         "executionId":"anExecution",
         "owner":"anOwner",
         "parentTaskId":"aParentId",
         "priority":42,
         "processDefinitionId":"aProcDefId",
         "processInstanceId":"aProcInstId",
         "caseDefinitionId":"aCaseDefId",
         "caseInstanceId":"aCaseInstId",
         "caseExecutionId":"aCaseExecution",
         "taskDefinitionKey":"aTaskDefinitionKey",
         "suspended": false,
         "formKey":"aFormKey",
         "tenantId": "aTenantId"
    }
]
```

## Download File
Download a file which is a variable in the scope of a task.

### `GET /api/task/{task-id}/file`

### Parameters:
| Name | Type | Description | Required |
| ---- | ---------- | ----------- | -------- |
| task-id | path | The id of the task having requested file it its scope | Yes |
| filePath | query | Path to requested file. It means either chain of forward slash-separated variables from the top variable to the file variable (e.g. `var1/var2/fileVar`), or just file variable name (e.g. `fileVar`)  | Yes |

### Result:
Requested file in a binary format.

### Response Codes:

| Code | Media Type | Description |
| ---- | ---------- | ----------- |
| 200 | \*/\* | Request successful |
| 403 |  | The user doesn't have an access to download the file. |
| 404 |  | Requested file is not found. |

### Example:

#### Request:

`GET /api/task/some-task-id/file/?filePath=/var1/var2/fileVar`

#### Response:
Status 200 OK.
```http request
200 OK
Content-Type: some/type; charset=utf-8
Content-Disposition: attachment; filename="fileFromFileVar.ext"
Content-Length: ...

[skipped content]
```

[Task Object description]: https://docs.camunda.org/manual/7.10/reference/rest/task/get-query/#result