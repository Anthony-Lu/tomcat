/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.tribes.group.interceptors;

import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.group.InterceptorPayload;
import org.apache.catalina.tribes.io.ChannelData;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.text.DecimalFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;



/**
 *
 *
 * @version 1.0
 */
public class ThroughputInterceptor extends ChannelInterceptorBase {
    protected static final StringManager sm = StringManager.getManager(ThroughputInterceptor.class);
    private static final Log log = LogFactory.getLog(ThroughputInterceptor.class);
    final AtomicLong msgTxCnt = new AtomicLong(1);
    final AtomicLong msgRxCnt = new AtomicLong(0);
    final AtomicLong msgTxErr = new AtomicLong(0);
    final AtomicInteger access = new AtomicInteger(0);
    final DecimalFormat df = new DecimalFormat("#0.00");
    double mbTx = 0;
    double mbAppTx = 0;
    double mbRx = 0;
    double timeTx = 0;
    double lastCnt = 0;
    int interval = 10000;
    long txStart = 0;
    long rxStart = 0;

    @Override
    public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload) throws ChannelException {
        if ( access.addAndGet(1) == 1 ) txStart = System.currentTimeMillis();
        long bytes = XByteBuffer.getDataPackageLength(((ChannelData)msg).getDataPackageLength());
        try {
            super.sendMessage(destination, msg, payload);
        }catch ( ChannelException x ) {
            msgTxErr.addAndGet(1);
            if ( access.get() == 1 ) access.addAndGet(-1);
            throw x;
        }
        mbTx += (bytes*destination.length)/(1024d*1024d);
        mbAppTx += bytes/(1024d*1024d);
        if ( access.addAndGet(-1) == 0 ) {
            long stop = System.currentTimeMillis();
            timeTx += (stop - txStart) / 1000d;
            if ((msgTxCnt.get() / interval) >= lastCnt) {
                lastCnt++;
                report(timeTx);
            }
        }
        msgTxCnt.addAndGet(1);
    }

    @Override
    public void messageReceived(ChannelMessage msg) {
        if ( rxStart == 0 ) rxStart = System.currentTimeMillis();
        long bytes = XByteBuffer.getDataPackageLength(((ChannelData)msg).getDataPackageLength());
        mbRx += bytes/(1024d*1024d);
        msgRxCnt.addAndGet(1);
        if ( msgRxCnt.get() % interval == 0 ) report(timeTx);
        super.messageReceived(msg);

    }

    public void report(double timeTx) {
        if ( log.isInfoEnabled() )
            log.info(sm.getString("throughputInterceptor.report",
                    msgTxCnt, df.format(mbTx), df.format(mbAppTx), df.format(timeTx), df.format(mbTx/timeTx),
                    df.format(mbAppTx/timeTx), msgTxErr, msgRxCnt, df.format(mbRx/((System.currentTimeMillis()-rxStart)/1000)),
                    df.format(mbRx)));
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public double getLastCnt() {
        return lastCnt;
    }

    public double getMbAppTx() {
        return mbAppTx;
    }

    public double getMbRx() {
        return mbRx;
    }

    public double getMbTx() {
        return mbTx;
    }

    public AtomicLong getMsgRxCnt() {
        return msgRxCnt;
    }

    public AtomicLong getMsgTxCnt() {
        return msgTxCnt;
    }

    public AtomicLong getMsgTxErr() {
        return msgTxErr;
    }

    public long getRxStart() {
        return rxStart;
    }

    public double getTimeTx() {
        return timeTx;
    }

    public long getTxStart() {
        return txStart;
    }

}
