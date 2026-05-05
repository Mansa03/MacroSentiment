package com.mirror.dagger;

import com.mirror.NewsIngestionService;

import dagger.Component;
import javax.inject.Singleton;

@Component(modules = {ApplicationModule.class, EnvironmentModule.class, ResourceModule.class})
@Singleton
public interface ApplicationComponent {
    NewsIngestionService newsIngestionService();
    
}
