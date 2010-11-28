/**
 * Copyright (C) 2010 Peter Karich <jetwick_@_pannous_._info>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jetwick.tw;

import com.google.inject.Inject;
import de.jetwick.config.Configuration;
import de.jetwick.solr.SolrTweet;
import de.jetwick.tw.queue.AbstractTweetPackage;
import de.jetwick.tw.queue.TweetPackage;
import de.jetwick.util.StopWatch;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * stores the tweets from the queue into the dbHelper and solr
 * 
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class TweetConsumer extends AbstractTweetConsumer {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private BlockingQueue<TweetPackage> tweetPackages;
    // collect at least those tweets before feeding
    private int tweetBatchSize = 1;
    private long tweetBatchTime = 60 * 1000;
    private long lastFeed = System.currentTimeMillis();
    // do not optimize per default
    private int optimizeToSegmentsAfterUpdate = -1;
    private long optimizeInterval = -1;
    // optimize should not happen directly after start of tweet consumer / collector!
    private long lastOptimizeTime = System.currentTimeMillis();
    private StopWatch sw1;

    @Inject
    public TweetConsumer(Configuration cfg) {
        super("tweet-consumer", cfg);
    }

    @Override
    public void run() {
        logger.info("tweets per session:" + tweetBatchSize);
        while (!isInterrupted()) {
            if (tweetPackages.isEmpty()) {
                // do only break if tweets are empty AND producer is death
                if (!producer.isAlive())
                    break;

                // TODO instead of a 'fixed' waiting               
                // use producer.getCondition().await + signalAll
                if (!myWait(1))
                    break;

                continue;
            }

            // make sure we really use the commit batch size
            // because solr doesn't want too frequent commits
            int count = AbstractTweetPackage.calcNumberOfTweets(tweetPackages);
            if (count < tweetBatchSize && producer.isAlive() && System.currentTimeMillis() - lastFeed < tweetBatchTime)
                continue;

            if (count == 0)
                continue;

            lastFeed = System.currentTimeMillis();
            sw1 = new StopWatch(" ");
            sw1.start();
            Collection<SolrTweet> res = updateTweets(tweetPackages, tweetBatchSize);
            sw1.stop();
            String str = "[solr] " + sw1.toString() + "\t updateCount=" + res.size();
            long time = System.currentTimeMillis();

            if (optimizeInterval > 0)
                str += "; next optimize in: " + (optimizeInterval - (time - lastOptimizeTime)) / 3600f / 1000f + "h ";

            logger.info(str);
            if (optimizeToSegmentsAfterUpdate > 0) {
                if (optimizeInterval > 0 && time - lastOptimizeTime >= optimizeInterval) {
                    lastOptimizeTime = time;
                    UpdateResponse orsp = tweetSearch.optimize(optimizeToSegmentsAfterUpdate);
                    logger.info("[solr] optimized: " + orsp.getElapsedTime() / 1000.0f
                            + " to segments:" + optimizeToSegmentsAfterUpdate);
                }
            }
        }
        logger.info(getName() + " finished");
    }

    public void setTweetPackages(BlockingQueue<TweetPackage> tweets) {
        this.tweetPackages = tweets;
    }

    public void setTweetBatchSize(int tweetBatchSize) {
        this.tweetBatchSize = tweetBatchSize;
    }

    /**
     * @param optimizeInterval     
     *        in the form of 2     (i.e. every 2 hours)
     */
    public void setOptimizeInterval(String optimizeStr) {
        optimizeInterval = -1;

        if (optimizeStr == null)
            return;

        optimizeStr = optimizeStr.trim();
        try {
            int index = optimizeStr.indexOf(":");
            if (index >= 0)
                logger.warn("Not supported ony longer because it can be that optimized is triggered several times!");
            else
                optimizeInterval = Long.parseLong(optimizeStr) * 3600 * 1000;
        } catch (Exception ex) {
            logger.warn("Optimization disabled! " + ex.getLocalizedMessage());
        }
    }

    public void setOptimizeToSegmentsAfterUpdate(int optimizeToSegmentsAfterUpdate) {
        this.optimizeToSegmentsAfterUpdate = optimizeToSegmentsAfterUpdate;
    }
}
