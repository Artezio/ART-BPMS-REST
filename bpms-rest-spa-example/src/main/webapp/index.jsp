<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jstl/core" prefix="c" %>

<!doctype html>
<html lang="ru">

<%
    final String KEYCLOAK_SERVER_URL = System.getProperty("KEYCLOAK_SERVER_URL", "http://localhost:8180/auth");
    final String BPMS_REST_API = System.getProperty("BPMS_REST_API", "http://localhost:8080/bpms-rest/api");
    final String KEYCLOAK_REALM = System.getProperty("KEYCLOAK_REALM", "master");
    final String KEYCLOAK_CLIENT_ID = System.getProperty("KEYCLOAK_CLIENT_ID", "bpms-rest");
    final String FILE_STORAGE_URL = System.getenv("FILE_STORAGE_URL");
%>

<head>
    <title>Dashboard</title>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
    <link rel='stylesheet' href='css/bootstrap-4.1.3.min.css'>
    <link rel="stylesheet" href="css/formio.full-4.7.8.min.css">
    <link rel="stylesheet" href="css/index.css">
    <script src="<%=KEYCLOAK_SERVER_URL%>/js/keycloak.js" type="application/javascript"></script>
    <script src="js/lib/jquery-3.3.1.min.js" type="application/javascript"></script>
    <script src="js/lib/formio.full-4.7.8.min.js" type="application/javascript"></script>
    <script src="js/lib/bootstrap-4.1.3.min.js" type="application/javascript"></script>
    <script src='js/bpms-rest-spa.js' type="application/javascript"></script>
    <style type="text/css">
        .table-borderless table,
        .table-borderless tbody>tr>td,
        .table-borderless tbody>tr>th,
        .table-borderless tfoot>tr>td,
        .table-borderless tfoot>tr>th,
        .table-borderless thead>tr>td,
        .table-borderless thead>tr>th {
            border: none;
        }
    </style>
</head>

<body>
    <nav class="navbar-head">
        <div class="navbar-head__logo">
            <img src="assets/logo.svg" alt="">
        </div>
        <div class="navbar-head__right-nav">
            <div><span id="username" class="greeting">Welcome, clinician</span></div>
            <div><a onclick="keycloak.logout()"><img src="assets/logout.svg" alt="Logout"></a></div>
        </div>
    </nav>

    <div class="main-content">
        <div class="sidebar">
            <div class="accordion sidebar__tab" id="sidebar__assigned-tasks">
                <div class="sidebar__tab__tab-link" data-toggle="collapse" data-target="#sidebar__assigned-tasks__tab"
                    aria-expanded="true" aria-controls="sidebar__assigned-tasks__tab" title="My tasks">
                    <span class="sidebar__tab-item-text" title="">My tasks</span></div>
                <div class="sidebar__tab__tab-content collapse show" id="sidebar__assigned-tasks__tab"
                    aria-labelledby="headline__assigned-tasks" data-parent="#sidebar__assigned-tasks">
                    <div id="assigned-tasks" class="list-group"></div>
                </div>
            </div>
            <div class="accordion" id="sidebar__available-tasks">
                <div class="sidebar__tab">
                    <div class="sidebar__tab__tab-link" data-toggle="collapse"
                        data-target="#sidebar__available-tasks__tab" aria-expanded="true"
                        aria-controls="sidebar__available-tasks__tab" title="Available tasks">
                        <span class="sidebar__tab-item-text">Available tasks</span></div>
                </div>
                <div class="sidebar__tab__tab-content collapse show" id="sidebar__available-tasks__tab"
                    aria-labelledby="headline__assigned-tasks" data-parent="#sidebar__available-tasks">
                    <div id="available-tasks" class="list-group"></div>
                </div>
            </div>
            <div class="accordion" id="sidebar__startable-processes">
                <div class="sidebar__tab">
                    <div class="sidebar__tab__tab-link" data-toggle="collapse"
                        data-target="#sidebar__startable-processes__process" aria-expanded="true"
                        aria-controls="sidebar__startable-processes__process" title="Processes available to start">
                        <span class="sidebar__tab-item-text">Processes available to start</span></div>
                </div>
                <div class="sidebar__tab__tab-content collapse show" id="sidebar__startable-processes__process"
                    aria-labelledby="headline__assigned-tasks" data-parent="#sidebar__startable-processes">
                    <div id="startable-processes" class="list-group"></div>
                </div>
            </div>
        </div>

        <div id="error" style="display: none;">
            <div class="error-header">
                <img src="./assets/error.svg" alt="error">
                <span class="error-header__text">Internal server error</span>
            </div>
            <div id="error-text"></div>
        </div>
        <div id="current-form" class="form-container">
            <h2 class="form-name">Form name</h2>
            <div id="formio"></div>
        </div>
    </div>
</body>

<script type="text/javascript">

    let fileStorageUrl = '<%=FILE_STORAGE_URL%>';
    let bpmsRestApi = '<%=BPMS_REST_API%>';
    let keycloakConfig = {
        'url': '<%=KEYCLOAK_SERVER_URL%>',
        'realm': '<%=KEYCLOAK_REALM%>',
        'clientId': '<%=KEYCLOAK_CLIENT_ID%>'
    };

    let keycloak = Keycloak(keycloakConfig);
    keycloak.init({ onLoad: 'login-required', "checkLoginIframe": false })
        .success(function () {
            loadProcessesAndTasks();
            keycloak.loadUserInfo()
                .success(info => {
                    let fullUserName = getFullUserName(info.preferred_username);
                    $('#username').html('<strong>Welcome, ' + fullUserName + '!</strong>')
                })
        })
        .error(function (e) {
            alert('Authentication error ' + e);
        });
</script>

</html>