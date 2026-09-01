package com.stockbatch.kismasterreconciliation;

import com.stockapp.domain.stock.KisMasterSyncExecutionRepository;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockMasterStatusHistoryRepository;
import com.stockapp.domain.stock.StockRepository;
import com.stockapp.external.kis.master.KisKosdaqMasterParser;
import com.stockapp.external.kis.master.KisKospiMasterParser;
import com.stockapp.external.kis.master.KisMasterArchiveReader;
import com.stockapp.external.kis.master.KisMasterClient;
import com.stockapp.external.kis.master.KisMasterDownloader;
import com.stockapp.external.kis.master.KisMasterInstrumentClassifier;
import com.stockapp.external.kis.master.KisMasterInstrumentPolicy;
import com.stockapp.external.kis.master.KisMasterParserRouter;
import com.stockapp.external.kis.master.KisMasterReconciliationPlanner;
import com.stockapp.external.kis.master.KisMasterReconciliationPublisher;
import com.stockapp.external.kis.master.KisMasterReconciliationService;
import com.stockapp.external.kis.master.KisMasterSnapshotFactory;
import com.stockapp.external.kis.master.KisMasterSnapshotValidator;
import com.stockapp.external.kis.master.KisMasterSyncExecutionFailureRecorder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan(basePackageClasses = Stock.class)
@EnableJpaRepositories(basePackageClasses = StockRepository.class)
@EnableConfigurationProperties(KisMasterReconciliationProperties.class)
public class KisMasterReconciliationApplication {

    @Bean
    KisMasterDownloader downloader(RestClient.Builder builder) {
        return new KisMasterDownloader(builder);
    }

    @Bean
    KisMasterArchiveReader archiveReader() {
        return new KisMasterArchiveReader();
    }

    @Bean
    KisMasterParserRouter parserRouter() {
        return new KisMasterParserRouter(List.of(
                new KisKospiMasterParser(), new KisKosdaqMasterParser()));
    }

    @Bean
    KisMasterClient client(
            KisMasterDownloader downloader,
            KisMasterArchiveReader archiveReader,
            KisMasterParserRouter parserRouter
    ) {
        return new KisMasterClient(downloader, archiveReader, parserRouter);
    }

    @Bean
    KisMasterSnapshotFactory snapshotFactory(Clock clock) {
        return new KisMasterSnapshotFactory(
                new KisMasterInstrumentClassifier(),
                new KisMasterInstrumentPolicy(),
                new KisMasterSnapshotValidator(),
                clock);
    }

    @Bean
    KisMasterReconciliationPlanner planner(
            StockRepository stockRepository,
            KisMasterSyncExecutionRepository executionRepository
    ) {
        return new KisMasterReconciliationPlanner(stockRepository, executionRepository);
    }

    @Bean
    KisMasterReconciliationPublisher publisher(
            StockRepository stockRepository,
            KisMasterSyncExecutionRepository executionRepository,
            StockMasterStatusHistoryRepository historyRepository,
            Clock clock
    ) {
        return new KisMasterReconciliationPublisher(
                stockRepository, executionRepository, historyRepository, clock);
    }

    @Bean
    KisMasterSyncExecutionFailureRecorder failureRecorder(
            KisMasterSyncExecutionRepository executionRepository,
            Clock clock
    ) {
        return new KisMasterSyncExecutionFailureRecorder(executionRepository, clock);
    }

    @Bean
    KisMasterReconciliationService reconciliationService(
            KisMasterReconciliationPublisher publisher,
            KisMasterSyncExecutionFailureRecorder failureRecorder
    ) {
        return new KisMasterReconciliationService(publisher, failureRecorder);
    }

    @Bean
    KisMasterReconciliationRunner runner(
            KisMasterReconciliationProperties properties,
            KisMasterClient client,
            KisMasterSnapshotFactory snapshotFactory,
            KisMasterReconciliationPlanner planner,
            KisMasterSyncExecutionRepository executionRepository,
            KisMasterReconciliationService reconciliationService,
            Clock clock
    ) {
        return new KisMasterReconciliationRunner(
                properties, client, snapshotFactory, planner,
                executionRepository, reconciliationService, clock);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(
                KisMasterReconciliationApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles("kis-master-reconciliation");
        ConfigurableApplicationContext context = null;
        try {
            context = application.run(args);
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }
}
