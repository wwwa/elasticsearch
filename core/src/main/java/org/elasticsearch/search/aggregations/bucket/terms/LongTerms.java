/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.search.aggregations.bucket.terms;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.DocValueFormat;
import org.elasticsearch.search.aggregations.AggregationStreams;
import org.elasticsearch.search.aggregations.InternalAggregations;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class LongTerms extends InternalTerms<LongTerms, LongTerms.Bucket> {

    public static final Type TYPE = new Type("terms", "lterms");

    public static final AggregationStreams.Stream STREAM = new AggregationStreams.Stream() {
        @Override
        public LongTerms readResult(StreamInput in) throws IOException {
            LongTerms buckets = new LongTerms();
            buckets.readFrom(in);
            return buckets;
        }
    };

    public static void registerStreams() {
        AggregationStreams.registerStream(STREAM, TYPE.stream());
    }

    static class Bucket extends InternalTerms.Bucket {

        long term;

        public Bucket(DocValueFormat format, boolean showDocCountError) {
            super(format, showDocCountError);
        }

        public Bucket(long term, long docCount, InternalAggregations aggregations, boolean showDocCountError, long docCountError,
                DocValueFormat format) {
            super(docCount, aggregations, showDocCountError, docCountError, format);
            this.term = term;
        }

        @Override
        public String getKeyAsString() {
            return format.format(term);
        }

        @Override
        public Object getKey() {
            return term;
        }

        @Override
        public Number getKeyAsNumber() {
            return term;
        }

        @Override
        int compareTerm(Terms.Bucket other) {
            return Long.compare(term, ((Number) other.getKey()).longValue());
        }

        @Override
        Bucket newBucket(long docCount, InternalAggregations aggs, long docCountError) {
            return new Bucket(term, docCount, aggs, showDocCountError, docCountError, format);
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            term = in.readLong();
            docCount = in.readVLong();
            docCountError = -1;
            if (showDocCountError) {
                docCountError = in.readLong();
            }
            aggregations = InternalAggregations.readAggregations(in);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeLong(term);
            out.writeVLong(getDocCount());
            if (showDocCountError) {
                out.writeLong(docCountError);
            }
            aggregations.writeTo(out);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            builder.field(CommonFields.KEY, term);
            if (format != DocValueFormat.RAW) {
                builder.field(CommonFields.KEY_AS_STRING, format.format(term));
            }
            builder.field(CommonFields.DOC_COUNT, getDocCount());
            if (showDocCountError) {
                builder.field(InternalTerms.DOC_COUNT_ERROR_UPPER_BOUND_FIELD_NAME, getDocCountError());
            }
            aggregations.toXContentInternal(builder, params);
            builder.endObject();
            return builder;
        }
    }

    LongTerms() {} // for serialization

    public LongTerms(String name, Terms.Order order, DocValueFormat format, int requiredSize, int shardSize, long minDocCount,
            List<? extends InternalTerms.Bucket> buckets, boolean showTermDocCountError, long docCountError, long otherDocCount,
            List<PipelineAggregator> pipelineAggregators, Map<String, Object> metaData) {
        super(name, order, format, requiredSize, shardSize, minDocCount, buckets, showTermDocCountError, docCountError, otherDocCount,
                pipelineAggregators, metaData);
    }

    @Override
    public Type type() {
        return TYPE;
    }

    @Override
    public LongTerms create(List<Bucket> buckets) {
        return new LongTerms(this.name, this.order, this.format, this.requiredSize, this.shardSize, this.minDocCount, buckets,
                this.showTermDocCountError, this.docCountError, this.otherDocCount, this.pipelineAggregators(), this.metaData);
    }

    @Override
    public Bucket createBucket(InternalAggregations aggregations, Bucket prototype) {
        return new Bucket(prototype.term, prototype.docCount, aggregations, prototype.showDocCountError, prototype.docCountError,
                prototype.format);
    }

    @Override
    protected LongTerms create(String name, List<org.elasticsearch.search.aggregations.bucket.terms.InternalTerms.Bucket> buckets,
            long docCountError, long otherDocCount, InternalTerms prototype) {
        return new LongTerms(name, prototype.order, ((LongTerms) prototype).format, prototype.requiredSize, prototype.shardSize,
                prototype.minDocCount, buckets, prototype.showTermDocCountError, docCountError, otherDocCount, prototype.pipelineAggregators(),
                prototype.getMetaData());
    }

    @Override
    protected void doReadFrom(StreamInput in) throws IOException {
        this.docCountError = in.readLong();
        this.order = InternalOrder.Streams.readOrder(in);
        this.format = in.readNamedWriteable(DocValueFormat.class);
        this.requiredSize = readSize(in);
        this.shardSize = readSize(in);
        this.showTermDocCountError = in.readBoolean();
        this.minDocCount = in.readVLong();
        this.otherDocCount = in.readVLong();
        int size = in.readVInt();
        List<InternalTerms.Bucket> buckets = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Bucket bucket = new Bucket(format, showTermDocCountError);
            bucket.readFrom(in);
            buckets.add(bucket);
        }
        this.buckets = buckets;
        this.bucketMap = null;
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeLong(docCountError);
        InternalOrder.Streams.writeOrder(order, out);
        out.writeNamedWriteable(format);
        writeSize(requiredSize, out);
        writeSize(shardSize, out);
        out.writeBoolean(showTermDocCountError);
        out.writeVLong(minDocCount);
        out.writeVLong(otherDocCount);
        out.writeVInt(buckets.size());
        for (InternalTerms.Bucket bucket : buckets) {
            bucket.writeTo(out);
        }
    }

    @Override
    public XContentBuilder doXContentBody(XContentBuilder builder, Params params) throws IOException {
        builder.field(InternalTerms.DOC_COUNT_ERROR_UPPER_BOUND_FIELD_NAME, docCountError);
        builder.field(SUM_OF_OTHER_DOC_COUNTS, otherDocCount);
        builder.startArray(CommonFields.BUCKETS);
        for (InternalTerms.Bucket bucket : buckets) {
            bucket.toXContent(builder, params);
        }
        builder.endArray();
        return builder;
    }

}
