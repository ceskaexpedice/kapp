package cz.inovatika.model;

import java.net.http.HttpClient;


public record HarvesterContext(
        HarvestingItem item,
        HttpClient client,
        Instance config
){}
