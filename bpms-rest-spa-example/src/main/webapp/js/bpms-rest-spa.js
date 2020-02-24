Formio.icons = 'fontawesome';

function refreshToken() {
    return new Promise(function (resolve, reject) {
        keycloak.updateToken(180)
            .success(_ => {
                console.log("Resolving reftoken");
                resolve();
            })
            .error(_ => keycloak.login());
    });
}

let AuthTokenRefreshingPlugin = {
    priority: 0,
    preRequest: function (args) {
        return refreshToken();
    }
};

Formio.registerPlugin(AuthTokenRefreshingPlugin, 'authTokenRefresher');

function showError(response) {
    let errorCause = response ? response.cause : {};
    let errorMessage = response ? response.errorMessage : "";

    while (!isEmpty(errorCause)) {
        errorMessage = errorCause.errorMessage;
        errorCause = errorCause.cause;
    }

    function isEmpty(obj) {
        for (let key in obj) {
            return false;
        }
        return true;
    }

    //let serverErrorMessage = window.form
    //    ? window.form.i18next.getDataByLanguage('ru').translation['Internal server error']
    //    : "";
    $('#error').show().text("Internal server error: " + errorMessage);
}

function clearErrors() {
    $('#error').hide();
}

function closeCurrentForm() {
    $('#current-form .title').text("");
    $('#formio').empty();
}

function loadProcessesAndTasks() {
    closeCurrentForm();
    loadResources();
}

function loadResources() {
    loadAssignedTasks();
    loadAvailableTasks();
    loadStartableProcesses();
}

function loadNextTaskOrCloseForm(response) {
    console.log(response);
    if ((response !== undefined) && (response.id)) {
        console.log("Current form title will be " + response.name);
        $('#current-form .title').text(response.name);
        TaskForms.load(response.id);
    } else {
        closeCurrentForm();
    }
    loadResources();
}

function startProcessWithoutStartForm(processDefinitionKey) {
    clearErrors();
    refreshToken().then(
        $.ajax({
            method: 'POST',
            url: bpmsRestApi + '/process-definition/key/' + processDefinitionKey + '/start',
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            headers: {
                'Authorization': 'Bearer ' + keycloak.token
            },
            data: "{}"
        })).then(
            response => {
                loadNextTaskOrCloseForm(response);
            }, (xhr, textStatus) => {
                if (xhr.status === 401) {
                    keycloak.login();
                } else {
                    showError(xhr.responseJSON);
                }
            });
}

function parseProcessDefinitions(processDefinitions) {
    var processDefinitionList = $('#startable-processes');
    processDefinitionList.empty();
    $.each(processDefinitions, function () {
        var key = this.key;
        var id = this.id;
        var name = this.name;
        var startProcessFunction;
        if (this.hasStartFormKey === true) {
            startProcessFunction = function () {
                closeCurrentForm();
                $('#current-form .title').text(name);
                ProcessStartForms.load(id, key);
            }
        } else {
            startProcessFunction = function () {
                closeCurrentForm();
                $('#current-form .title').text(name);
                $('#formio').append(
                    $('<button>')
                        .attr('class', 'btn btn-primary')
                        .text('Start the process')
                        .click(() => startProcessWithoutStartForm(key))
                );
            }
        }
        processDefinitionList.append(
            $('<button>')
                .attr('class', 'list-group-item list-group-item-info')
                .text(name)
                .click(startProcessFunction)
        );
    });
}

function loadStartableProcesses() {
    $('#startable-processes').empty();
    refreshToken().then(_ =>
        $.ajax({
            method: 'GET',
            crossDomain: true,
            url: bpmsRestApi + '/process-definition',
            dataType: 'json',
            contentType: "application/json; charset=utf-8",
            headers: {
                'Authorization': 'Bearer ' + keycloak.token
            }
        })).then(
            processDefinitions => parseProcessDefinitions(processDefinitions),
            (xhr, textStatus) => {
                if (xhr.status === 401) {
                    keycloak.login();
                } else {
                    showError(xhr.responseJSON);
                }
            });
}

function setAssignedTasks(tasks) {
    var taskList = $('#assigned-tasks');
    taskList.empty();
    tasks.forEach(def => {
        var taskName = def.name;
        var taskId = def.id;
        taskList.append(
            $('<button>')
                .attr('class', 'list-group-item list-group-item-info')
                .text(taskName.replace(/^(\D+?)"(\d{7})"$/, fullUserNameReplacer))
                .click(
                    function () {
                        closeCurrentForm();
                        $('#current-form .title').text(taskName.replace(/^(\D+?)"(\d{7})"$/, fullUserNameReplacer));
                        TaskForms.load(taskId);
                    }
                ));
    });
}

function loadAssignedTasks() {
    $('#assigned-tasks').empty();
    refreshToken().then(_ =>
        $.ajax({
            method: 'GET',
            url: bpmsRestApi + '/task/assigned',
            dataType: 'json',
            headers: {
                'Authorization': 'Bearer ' + keycloak.token
            },
            cors: true
        })).then(
            tasks => setAssignedTasks(tasks),
            (xhr, textStatus) => {
                if (xhr.status === 401) {
                    keycloak.login();
                } else {
                    showError(xhr.responseJSON);
                }
            });
}

function setAvailableTasks(tasks) {
    var taskList = $('#available-tasks');
    taskList.empty();
    tasks.forEach(def => {
        var taskName = def.name;
        var taskId = def.id;
        taskList.append(
            $('<button>')
                .attr('class', 'list-group-item list-group-item-info')
                .text(taskName.replace(/^(\D+?)"(\d{7})"$/, fullUserNameReplacer))
                .click(
                    function () {
                        claimTask(taskId);
                    }
                ));
    });
}

function loadAvailableTasks() {
    $('#available-tasks').empty();
    refreshToken().then(_ =>
        $.ajax({
            method: 'GET',
            url: bpmsRestApi + '/task/available',
            dataType: 'json',
            headers: {
                'Authorization': 'Bearer ' + keycloak.token
            }
        })).then(
            tasks => setAvailableTasks(tasks),
            (xhr) => {
                showError(xhr.responseJSON);
            });
}

function claimTask(taskId) {
    refreshToken().then(_ =>
        $.ajax({
            method: 'POST',
            url: bpmsRestApi + '/task/' + taskId + '/claim',
            headers: {
                'Authorization': 'Bearer ' + keycloak.token
            }
        })).then(_ => {
            loadProcessesAndTasks();
        }, (xhr, textStatus) => {
            if (xhr.status === 401) {
                keycloak.login();
            } else {
                showError(xhr.responseJSON);
            }
        });
}

function prepareEnvironmentForFormio(processDefinitionId, processDefinitionKey) {
    return loadBaseUrl(processDefinitionId, processDefinitionKey)
        .then(setBaseUrl)
    // .then(loadCustomComponents)
    // .then(registerCustomComponents)
}

function loadBaseUrl(processDefinitionId, processDefinitionKey) {
    return $.ajax({
        method: 'GET',
        url: `${bpmsRestApi}/deployment/public-resources?process-definition-id=${processDefinitionId}&form-key=${processDefinitionKey}`,
        headers: {
            'Authorization': 'Bearer ' + keycloak.token
        }
    }).then(data => {
        if (data && data._links && data._links.resourcesBaseUrl && data._links.resourcesBaseUrl.href) {
            return data._links.resourcesBaseUrl.href;
        }
        throw new Error('Not a hal format');
    })
}

function setBaseUrl(url) {
    const base = document.createElement('base');
    base.href = url[url.length - 1] === '/' ? url : url + '/';
    document.head.append(base);
}

function registerCustomComponents() {

}

var ProcessStartForms = {
    submit: function (processDefinitionKey, data) {
        clearErrors();
        return refreshToken()
            .then(_ =>
                $.ajax({
                    method: 'POST',
                    url: bpmsRestApi + '/process-definition/key/' + processDefinitionKey + '/start',
                    data: JSON.stringify(data),
                    dataType: "json",
                    contentType: "application/json; charset=utf-8",
                    headers: {
                        'Authorization': 'Bearer ' + keycloak.token
                    }
                }));
    },

    load: function (processDefinitionId, processDefinitionKey, errorCallback) {
        clearErrors();
        refreshToken()
            .then(prepareEnvironmentForFormio(processDefinitionId, processDefinitionKey))
            .then(_ =>
                $.ajax({
                    method: 'GET',
                    url: bpmsRestApi + '/process-definition/key/' + processDefinitionKey + '/rendered-form',
                    headers: {
                        'Authorization': 'Bearer ' + keycloak.token
                    },

                })).then(data => {
                    Formio
                        .createForm(
                            document.getElementById('formio'),
                            data, { readOnly: false, noAlerts: true })
                        .then(form => {
                            form.submission = {
                                data: data.data
                            };
                            window.form = form;
                            let fileComponentNames = findFileComponentNames(form);
                            merge(data.data, form.data, fileComponentNames);
                            internationalize(form.components);

                            form.redraw();

                            let submitValidation = form.checkValidity;
                            let rejectValidation = () => true;

                            form.components
                                .filter(comp => comp.originalComponent.action === 'saveState')
                                .forEach(comp => {
                                    let formioButtonClickHandler = comp.eventHandlers.find(handler => handler.type === 'click').func;
                                    comp.removeEventListener(comp.buttonElement, 'click');
                                    let isNoValidationFlow = comp.originalComponent.properties['skipValidation'] === 'true';
                                    comp.addEventListener(comp.buttonElement, 'click', event => {
                                        form.checkValidity = isNoValidationFlow ? rejectValidation : submitValidation;
                                        formioButtonClickHandler(event);
                                    });
                                });

                            form.on('submit', submission => {
                                submission.saved = true;
                                let preparedToSubmissionData = {};
                                if (submission.state === 'submitted') {
                                    preparedToSubmissionData = submission.data;
                                }
                                preparedToSubmissionData.state = submission.state;
                                console.log('submission', submission);
                                ProcessStartForms.submit(processDefinitionKey, preparedToSubmissionData)
                                    .then(response => {
                                        loadNextTaskOrCloseForm(response);
                                    },
                                        (xhr, textStatus) => {
                                            if (xhr.status === 401) {
                                                keycloak.login();
                                            } else {
                                                showError(xhr.responseJSON);
                                                form.triggerRedraw();
                                            }
                                        }
                                    );
                                return submission;
                            });
                        });
                }, (xhr, textStatus) => {
                    if (xhr.status === 401) {
                        keycloak.login();
                    } else {
                        if (errorCallback !== undefined) {
                            errorCallback(xhr);
                        } else {
                            showError(xhr.responseJSON);
                        }
                    }
                });
    }
};

var TaskForms = {
    submit: function (taskId, data) {
        clearErrors();
        return refreshToken()
            .then(_ =>
                $.ajax({
                    method: 'POST',
                    url: bpmsRestApi + '/task/' + taskId + '/complete',
                    data: JSON.stringify(data),
                    dataType: "json",
                    contentType: "application/json; charset=utf-8",
                    headers: {
                        'Authorization': 'Bearer ' + keycloak.token
                    }
                }));
    },

    load: function (taskId) {
        clearErrors();
        refreshToken()
            .then(prepareEnvironmentForFormio)
            .then(_ =>
                $.ajax({
                    method: 'GET',
                    url: bpmsRestApi + '/task/' + taskId + '/rendered-form',
                    headers: {
                        'Authorization': 'Bearer ' + keycloak.token
                    }
                }))
            .then(data => {
                Formio
                    .createForm(
                        document.getElementById('formio'),
                        data, { readOnly: false, noAlerts: true })
                    .then(form => {
                        form.submission = {
                            data: data.data
                        }
                        window.form = form;
                        let fileComponentNames = findFileComponentNames(form);
                        merge(data.data, form.data, fileComponentNames);
                        form.redraw();

                        let submitValidation = form.checkValidity;
                        let rejectValidation = () => true;

                        form.components
                            .filter(comp => comp.originalComponent.action === 'saveState')
                            .forEach(comp => {
                                console.log("Replacing click for " + this);
                                var formioButtonClickHandler = comp.eventHandlers.find(handler => handler.type === 'click').func;
                                comp.removeEventListener(comp.buttonElement, 'click');
                                var isNoValidationFlow = comp.originalComponent.properties['skipValidation'] === 'true';
                                comp.addEventListener(comp.buttonElement, 'click', event => {
                                    form.checkValidity = isNoValidationFlow ? rejectValidation : submitValidation;
                                    formioButtonClickHandler(event);
                                });
                            });

                        form.on('submit', function (submission) {
                            submission.saved = true;
                            var preparedToSubmissionData = submission.data;
                            preparedToSubmissionData.state = submission.state;
                            console.log('submission', preparedToSubmissionData);
                            TaskForms.submit(taskId, preparedToSubmissionData)
                                .then(response => {
                                    loadNextTaskOrCloseForm(response);
                                },
                                    (xhr, textStatus) => {
                                        form.triggerRedraw();
                                        showError(xhr.responseJSON);
                                    });
                            return submission;
                        });
                    });
            }, (xhr, textStatus) => {
                if (xhr.status === 401) {
                    keycloak.login();
                } else {
                    showError(xhr.responseJSON);
                }
            })
    }
};

function merge(sourceData, targetData, fileComponentNames) {

    function isBase64DataUrl(url) {
        const base64DataUrlRegex = /^\s*data:([a-z-]+\/?[a-z-]+(;[a-z\-]+\=[a-z\-]+)?)?(;base64)?,[a-z0-9\!\$\&\'\,\(\)\*\+\,\;\=\-\.\_\~\:\@\/\?\%\s]*\s*$/i;
        return !!url.match(base64DataUrlRegex);
    }

    function getUrl(file) {
        if ((file.storage === "url") && (fileStorageUrl != null) && (fileStorageUrl !== "")) {
            if (isBase64DataUrl(file.url)) {
                return file.url;
            } else {
                return fileStorageUrl + "/" + file.url
            }
        } else {
            return file.url;
        }
    }

    for (let key in sourceData) {
        if (targetData.hasOwnProperty(key)) {
            if (sourceData[key] instanceof Object && !Array.isArray(sourceData[key])) {
                merge(sourceData[key], targetData[key], fileComponentNames);
            } else {
                let result = sourceData[key];
                if (fileComponentNames.includes(key)) {
                    result = result.map(value => {
                        var url = value.url;
                        value.url = getUrl(value);
                        if (isBase64DataUrl(url)) {
                            value.storage = "base64";
                        }
                        return value;
                    });
                }
                targetData[key] = result;
            }
        }
    }
}

function findFileComponentNames(container) {
    if (container.components) {
        return container.components
            .map(component => {
                switch (component.type) {
                    case 'form': {
                        return component.components
                            ? findFileComponentNames(component.components)
                            : findFileComponentNames(component.component.components);
                    }
                    case 'components':
                    case 'container': return findFileComponentNames(component);
                    case 'file': return component.key;
                    default: [];
                }
            })
            .flat()
            .filter(distinctAndNonUndefinedFilter);
    }
}

const distinctAndNonUndefinedFilter = (value, index, array) => {
    return array.indexOf(value) === index && value !== undefined;
};

function internationalize(components) {
    if (components) {
        components.forEach(component => {
            switch (component.type) {
                case 'form': {
                    component.components
                        ? internationalize(component.components)
                        : internationalize(component.component.components);
                } break;
                case 'components':
                case 'container': internationalize(component.components); break;
                case 'datetime': {
                    let fullComponent = Formio.Components.create(component);
                    applyI18nRuToDateTimeComponent(fullComponent);
                } break;
            }
        })
    }
}

function applyI18nRuToDateTimeComponent(fullDateTimeComponent) {
    let calendar = fullDateTimeComponent._widget.calendar;
    let res = i18n_ru.resources.ru.translation;
    calendar.l10n.months.longhand = [res.january, res.february, res.march, res.april, res.may, res.june, res.july, res.august,
    res.september, res.october, res.november, res.december];
    calendar.l10n.weekdays.shorthand = [res.sun, res.mon, res.tue, res.wed, res.thu, res.fri, res.sat]
}

function fullUserNameReplacer(match, taskName, userId) {
    return taskName + '"' + getFullUserName(userId) + '"';
}

function getFullUserName(userId) {
    switch (userId) {
        case '1234567': return 'John Price';
        case '7543324': return 'Jane White';
        case '5635654': return 'Steve Smith';
        default: return userId;
    }
}