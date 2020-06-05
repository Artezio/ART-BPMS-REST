package com.artezio;

import com.atomikos.icatch.jta.UserTransactionManager;
import org.camunda.bpm.engine.spring.SpringProcessEngineConfiguration;
import org.camunda.bpm.spring.boot.starter.annotation.EnableProcessApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.sql.DataSource;
import javax.transaction.TransactionManager;

@SpringBootApplication
@EnableWebMvc
@EnableProcessApplication
public class BpmsRestApplication {

    private final DataSourceProperties dataSourceProperties;

    @Autowired
    public BpmsRestApplication(DataSourceProperties dataSourceProperties) {
        this.dataSourceProperties = dataSourceProperties;
    }

    public static void main(String... args) {
        SpringApplication.run(BpmsRestApplication.class, args);
    }

    @Bean
    @ConfigurationProperties("spring.datasource.tomcat")
    public DataSource dataSource() {
        return dataSourceProperties.initializeDataSourceBuilder()
                .build();
    }

    @Bean
    public PlatformTransactionManager jtaTransactionManager() {
        TransactionManager txManager = new UserTransactionManager();
        return new JtaTransactionManager(txManager);
    }

    @Bean
    public SpringProcessEngineConfiguration processEngineConfiguration() {
        SpringProcessEngineConfiguration config = new SpringProcessEngineConfiguration();
        config.setDataSource(dataSource());
        config.setTransactionManager(jtaTransactionManager());
        config.setProcessEngineName("default");
        config.setDatabaseSchemaUpdate("true");
        config.setHistory("full");
        config.setJobExecutorActivate(true);
        config.setDefaultSerializationFormat("application/json");
        return config;
    }

}
