//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2007-2011 VMware, Inc.  All rights reserved.
//


package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Routing extends BrokerTestCase
{

    protected final String E = "MRDQ";
    protected final String Q1 = "foo";
    protected final String Q2 = "bar";

    protected void createResources() throws IOException {
        channel.exchangeDeclare(E, "direct");
        channel.queueDeclare(Q1, false, false, false, null);
        channel.queueDeclare(Q2, false, false, false, null);
    }

    protected void releaseResources() throws IOException {
        channel.queueDelete(Q1);
        channel.queueDelete(Q2);
        channel.exchangeDelete(E);
    }

    private void bind(String queue, String routingKey)
        throws IOException
    {
        channel.queueBind(queue, E, routingKey);
    }

    private void check(String routingKey, boolean expectQ1, boolean expectQ2)
        throws IOException
    {
        channel.basicPublish(E, routingKey, null, "mrdq".getBytes());
        checkGet(Q1, expectQ1);
        checkGet(Q2, expectQ2);
    }

    private void checkGet(String queue, boolean messageExpected)
        throws IOException
    {
        GetResponse r = channel.basicGet(queue, true);
        if (messageExpected) {
            assertNotNull(r);
        } else {
            assertNull(r);
        }
    }

    /**
     * Tests the "default queue name" and "default routing key" pieces
     * of the spec. See the doc for the "queue" and "routing key"
     * fields of queue.bind.
     */
    public void testMRDQRouting()
        throws IOException
    {
        bind(Q1, "baz");        //Q1, "baz"
        bind(Q1, "");           //Q1, ""
        bind("", "baz");        //Q2, "baz"
        bind("", "");           //Q2, Q2
        check("", true, false);
        check(Q1, false, false);
        check(Q2, false, true);
        check("baz", true, true);
    }

    /**
     * If a queue has more than one binding to an exchange, it should
     * NOT receive duplicate copies of a message that matches both
     * bindings.
     */
    public void testDoubleBinding()
        throws IOException
    {
        channel.queueBind(Q1, "amq.topic", "x.#");
        channel.queueBind(Q1, "amq.topic", "#.x");
        channel.basicPublish("amq.topic", "x.y", null, "x.y".getBytes());
        checkGet(Q1, true);
        checkGet(Q1, false);
        channel.basicPublish("amq.topic", "y.x", null, "y.x".getBytes());
        checkGet(Q1, true);
        checkGet(Q1, false);
        channel.basicPublish("amq.topic", "x.x", null, "x.x".getBytes());
        checkGet(Q1, true);
        checkGet(Q1, false);
    }

    public void testFanoutRouting() throws Exception {

        List<String> queues = new ArrayList<String>();

        for (int i = 0; i < 2; i++) {
            String q = "Q-" + System.nanoTime();
            channel.queueDeclare(q, false, true, true, null);
            channel.queueBind(q, "amq.fanout", "");
            queues.add(q);
        }

        channel.basicPublish("amq.fanout", System.nanoTime() + "",
                             null, "fanout".getBytes());

        for (String q : queues) {
            checkGet(q, true);
        }

        for (String q : queues) {
            channel.queueDelete(q);
        }
    }

    public void testTopicRouting() throws Exception {

        List<String> queues = new ArrayList<String>();

        //100+ queues is the trigger point for bug20046
        for (int i = 0; i < 100; i++) {
            channel.queueDeclare();
            AMQP.Queue.DeclareOk ok = channel.queueDeclare();
            String q = ok.getQueue();
            channel.queueBind(q, "amq.topic", "#");
            queues.add(q);
        }

        channel.basicPublish("amq.topic", "", null, "topic".getBytes());

        for (String q : queues) {
            checkGet(q, true);
        }
    }

    public void testHeadersRouting() throws Exception {
        Map<String, Object> spec = new HashMap<String, Object>();
        spec.put("h1", "12345");
        spec.put("h2", "bar");
        spec.put("h3", null);
        spec.put("x-match", "all");
        channel.queueBind(Q1, "amq.match", "", spec);
        spec.put("x-match", "any");
        channel.queueBind(Q2, "amq.match", "", spec);

        AMQP.BasicProperties props = new AMQP.BasicProperties();

        channel.basicPublish("amq.match", "", null, "0".getBytes());
        channel.basicPublish("amq.match", "", props, "0b".getBytes());

        props.setHeaders(new HashMap<String, Object>());
        props.getHeaders().put("h1", "12345");
        channel.basicPublish("amq.match", "", props, "1".getBytes());

        props.setHeaders(new HashMap<String, Object>());
        props.getHeaders().put("h1", 12345);
        channel.basicPublish("amq.match", "", props, "1b".getBytes());

        props.setHeaders(new HashMap<String, Object>());
        props.getHeaders().put("h2", "bar");
        channel.basicPublish("amq.match", "", props, "2".getBytes());

        props.setHeaders(new HashMap<String, Object>());
        props.getHeaders().put("h1", "12345");
        props.getHeaders().put("h2", "bar");
        channel.basicPublish("amq.match", "", props, "3".getBytes());

        props.setHeaders(new HashMap<String, Object>());
        props.getHeaders().put("h1", "12345");
        props.getHeaders().put("h2", "bar");
        props.getHeaders().put("h3", null);
        channel.basicPublish("amq.match", "", props, "4".getBytes());

        props.setHeaders(new HashMap<String, Object>());
        props.getHeaders().put("h1", "12345");
        props.getHeaders().put("h2", "quux");
        channel.basicPublish("amq.match", "", props, "5".getBytes());

        props.setHeaders(new HashMap<String, Object>());
        props.getHeaders().put("h1", "zot");
        props.getHeaders().put("h2", "quux");
        props.getHeaders().put("h3", null);
        channel.basicPublish("amq.match", "", props, "6".getBytes());

        props.setHeaders(new HashMap<String, Object>());
        props.getHeaders().put("h3", null);
        channel.basicPublish("amq.match", "", props, "7".getBytes());

        props.setHeaders(new HashMap<String, Object>());
        props.getHeaders().put("h1", "zot");
        props.getHeaders().put("h2", "quux");
        channel.basicPublish("amq.match", "", props, "8".getBytes());

        checkGet(Q1, true); // 4
        checkGet(Q1, false);

        checkGet(Q2, true); // 1
        checkGet(Q2, true); // 2
        checkGet(Q2, true); // 3
        checkGet(Q2, true); // 4
        checkGet(Q2, true); // 5
        checkGet(Q2, true); // 6
        checkGet(Q2, true); // 7
        checkGet(Q2, false);
    }

}

