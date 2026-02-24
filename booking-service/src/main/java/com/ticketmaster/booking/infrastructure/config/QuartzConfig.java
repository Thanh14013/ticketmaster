package com.ticketmaster.booking.infrastructure.config;

import com.ticketmaster.booking.application.scheduler.SeatReleaseScheduler;
import lombok.RequiredArgsConstructor;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

import javax.sql.DataSource;

/**
 * Quartz Scheduler configuration cho booking-service.
 *
 * <p><b>Mục đích:</b> Schedule {@link SeatReleaseScheduler} để tự động expire
 * booking sau 2 phút nếu user không thanh toán.
 *
 * <p><b>JDBC JobStore:</b> Jobs và triggers được persist vào bảng QRTZ_* trong PostgreSQL.
 * Điều này đảm bảo:
 * <ul>
 *   <li>Jobs không bị mất khi service restart</li>
 *   <li>Scheduler recover và chạy missed jobs khi service recover</li>
 *   <li>Cluster-safe (có thể scale booking-service nhiều replicas)</li>
 * </ul>
 *
 * <p><b>AutowireCapableJobFactory:</b> Cho phép Spring inject dependencies (@RequiredArgsConstructor)
 * vào Quartz Job classes. Mặc định Quartz không support Spring DI.
 */
@Configuration
@RequiredArgsConstructor
public class QuartzConfig {

    private final DataSource            dataSource;
    private final ApplicationContext    applicationContext;

    /**
     * Custom JobFactory cho phép Spring DI trong Quartz Jobs.
     * Không có bean này, Quartz sẽ throw NullPointerException khi inject dependencies.
     */
    @Bean
    public JobFactory jobFactory() {
        AutowireCapableJobFactory factory =
                new AutowireCapableJobFactory(applicationContext.getAutowireCapableBeanFactory());
        return factory;
    }

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(JobFactory jobFactory) {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();

        factory.setDataSource(dataSource);
        factory.setJobFactory(jobFactory);
        factory.setOverwriteExistingJobs(true);
        factory.setAutoStartup(true);
        factory.setWaitForJobsToCompleteOnShutdown(true);

        // Quartz properties đã cấu hình trong application.yml
        // (instanceId=AUTO, isClustered=false, tablePrefix=QRTZ_)

        return factory;
    }

    /**
     * Custom SpringBeanJobFactory cho phép autowire vào Quartz Job instances.
     */
    static class AutowireCapableJobFactory extends SpringBeanJobFactory
            implements ApplicationContextAware {

        private AutowireCapableBeanFactory beanFactory;

        AutowireCapableJobFactory(AutowireCapableBeanFactory beanFactory) {
            this.beanFactory = beanFactory;
        }

        @Override
        public void setApplicationContext(ApplicationContext context) {
            this.beanFactory = context.getAutowireCapableBeanFactory();
        }

        @Override
        protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
            Object job = super.createJobInstance(bundle);
            // Inject Spring beans (@Autowired / @RequiredArgsConstructor) vào Quartz job
            beanFactory.autowireBean(job);
            return job;
        }
    }
}