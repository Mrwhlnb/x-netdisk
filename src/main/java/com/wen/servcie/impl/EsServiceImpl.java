package com.wen.servcie.impl;

import com.alibaba.fastjson.JSON;
import com.wen.mapper.MyFileMapper;
import com.wen.pojo.MyFile;
import com.wen.servcie.EsService;
import com.wen.utils.KeyUtil;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Service
public class EsServiceImpl implements EsService {
    @Autowired
    @Qualifier("restHighLevelClient")
    RestHighLevelClient client;
    @Autowired
    MyFileMapper fileMapper;

    @Override
    public boolean addData(List<MyFile> list) {
        return false;
    }
    
    @Override
    public boolean addData(MyFile file) {
        IndexRequest request = new IndexRequest(KeyUtil.ES_INDEX);
        request.id(String.valueOf(file.getMyFileId()));
        request.timeout("1m");
        request.source(JSON.toJSONString(file), XContentType.JSON);

        try {
            IndexResponse resp = client.index(request, RequestOptions.DEFAULT);
            return resp.isFragment();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean updateData(MyFile file) {
        UpdateRequest request = new UpdateRequest(KeyUtil.ES_INDEX, String.valueOf(file.getMyFileId()));
        request.timeout("5s");
        request.doc(JSON.toJSONString(file), XContentType.JSON);
        try {
            UpdateResponse resp = client.update(request, RequestOptions.DEFAULT);
            return resp.isFragment();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean delDate(String id) {
        DeleteRequest request = new DeleteRequest(KeyUtil.ES_INDEX, id);
        request.timeout("5s");
        try {
            DeleteResponse resp = client.delete(request, RequestOptions.DEFAULT);
            return resp.isFragment();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

    }

    @Override
    public List<Map<String, Object>> searchData(int storeId, String keyword) throws IOException {
        LinkedList<Map<String, Object>> list = new LinkedList<>();
        SearchRequest request = new SearchRequest(KeyUtil.ES_INDEX);

        //两个条件 根据用户仓库 以及文件名 搜索
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must(QueryBuilders.termQuery("fileStoreId", storeId));
        boolQuery.must(QueryBuilders.matchQuery("myFileName", keyword));
        sourceBuilder.query(boolQuery);

        request.source(sourceBuilder);

        SearchResponse resp = client.search(request, RequestOptions.DEFAULT);
        for (SearchHit hit : resp.getHits().getHits()) {
            list.add(hit.getSourceAsMap());
        }
        return list;
    }

    @Override
    public boolean esWarmUp() {
        System.out.println("开始预热Elasticsearch");
        List<MyFile> files = fileMapper.queryAllFiles();
        BulkRequest bulkRequest = new BulkRequest();
        bulkRequest.timeout("1m");
        for (MyFile file : files) {
            bulkRequest.add(
                    new IndexRequest(KeyUtil.ES_INDEX)
                            .id(String.valueOf(file.getMyFileId()))
                            .source(JSON.toJSONString(file), XContentType.JSON)
            );
        }
        BulkResponse resp;
        try {
            resp = client.bulk(bulkRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return !resp.hasFailures();
    }


}
