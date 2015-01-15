package com.dummy.batch;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.dummy.batch.domain.Dummy;

/**
 * 
 * @author khimung
 *
 */
@SpringBootApplication
public class DummyJob {
	
	public static void main(String[] args) {
		ApplicationContext ctx = SpringApplication.run(DummyJob.class, args);

        List<Dummy> results = ctx.getBean(JdbcTemplate.class).query("SELECT * FROM dummy", new RowMapper<Dummy>() {
            @Override
            public Dummy mapRow(ResultSet rs, int row) throws SQLException {
                return new Dummy(rs.getInt(1));
            }
        });

        for (Dummy Dummy : results) {
            System.out.println("Found <" + Dummy + "> in the database.");
        }
	}
}
