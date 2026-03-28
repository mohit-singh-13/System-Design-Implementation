package systemdesign.backend.readreplicas.config;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;
import systemdesign.backend.readreplicas.utils.ReplicationRoutingDataSource;

@Configuration
public class DataSourceConfig {

	@Bean
	public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
		return new JpaTransactionManager(emf);
	}

	@Bean
	public LocalContainerEntityManagerFactoryBean entityManagerFactory(@Qualifier("dataSource") DataSource dataSource) {
		LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
		em.setDataSource(dataSource);
		em.setPackagesToScan("systemdesign.backend.readreplicas");

		HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
		em.setJpaVendorAdapter(vendorAdapter);

		return em;
	}

	@Bean(name = "masterDataSource")
	@ConfigurationProperties(prefix = "spring.datasource.master")
	public DataSource masterDataSource() {
		return DataSourceBuilder.create().build();
	}

	@Bean(name = "replicaDataSource")
	@ConfigurationProperties(prefix = "spring.datasource.replica")
	public DataSource replicaDataSource() {
		return DataSourceBuilder.create().build();
	}

	@Bean
	public DataSource routingDataSource() {
		ReplicationRoutingDataSource routing = new ReplicationRoutingDataSource();

		Map<Object, Object> map = new HashMap<>();

		map.put("master", masterDataSource());
		map.put("replica", replicaDataSource());

		routing.setTargetDataSources(map);
		routing.setDefaultTargetDataSource(masterDataSource());

		return routing;
	}

	@Bean
	public DataSource dataSource() {
		return new LazyConnectionDataSourceProxy(routingDataSource());
	}

	@Bean
	public JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}
}
