package com.example.sqliwebdemo;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DemoData {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public DemoData(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    public void reset() {
        // DELETE and explicit sample IDs keep the reset atomic; TRUNCATE would implicitly commit in MySQL.
        transactions.executeWithoutResult(tx -> {
            jdbc.update("DELETE FROM posts");
            jdbc.update("DELETE FROM users");
            new ResourceDatabasePopulator(new ClassPathResource("db/demo-data.sql")).execute(jdbc.getDataSource());
        });
    }
}
