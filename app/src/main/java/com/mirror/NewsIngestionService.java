package com.mirror;

import com.mirror.dagger.ApplicationComponent;
import com.mirror.dagger.DaggerApplicationComponent;
import com.mirror.ingestion.PollingManager;
import lombok.AllArgsConstructor;

import javax.inject.Inject;

@AllArgsConstructor(onConstructor = @__(@Inject))
public class NewsIngestionService {
    private PollingManager pollingManager;

    public static void main(String[] args) {
        ApplicationComponent component = DaggerApplicationComponent.create();
        NewsIngestionService service = component.newsIngestionService();
        service.pollingManager.startPolling();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            service.pollingManager.stopPolling();
        }));
    }
}