package com.mirror;

import javax.inject.Inject;
import lombok.AllArgsConstructor;
import com.mirror.ingestion.PollingManager;

import com.mirror.dagger.DaggerApplicationComponent;
import com.mirror.dagger.ApplicationComponent;

@AllArgsConstructor(onConstructor = @__(@Inject))
public class NewsIngestionService {
    private PollingManager pollingManager;
    
    public static void main(String[] args) {
        ApplicationComponent component = DaggerApplicationComponent.create();
        NewsIngestionService service = component.newsIngestionService();
        service.pollingManager.startPolling();
    }
    
   
}