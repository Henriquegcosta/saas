package br.com.vigentra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Executor das comunicações em segundo plano (pequeno pool; síncrono nos testes). */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "comunicacaoExecutor")
    public TaskExecutor comunicacaoExecutor(AppProperties props) {
        if (props.comunicacao() != null && !props.comunicacao().async()) return new SyncTaskExecutor();
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(500);
        ex.setThreadNamePrefix("comunicacao-");
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(20);
        ex.initialize();
        return ex;
    }
}
