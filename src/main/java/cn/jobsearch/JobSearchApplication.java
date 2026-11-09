package cn.jobsearch;

import cn.jobsearch.mcp.BossMcpServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JobSearchApplication {

    public static void main(String[] args) {
        if (java.util.Arrays.asList(args).contains("--mcp-stdio")) {
            BossMcpServer.run();
            return;
        }
        SpringApplication.run(JobSearchApplication.class, args);
    }
}
