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

package de.jetwick.rmi;

import com.google.inject.Inject;
import de.jetwick.config.Configuration;
import de.jetwick.tw.Twitter4JTweet;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Arrays;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.Tweet;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class RMIClient implements CommunicationService {

    private static Logger logger = LoggerFactory.getLogger(RMIServer.class);
    private CommunicationService service;
    private Configuration config;

    @Inject
    public RMIClient(Configuration config) {
        this.config = config;
    }

    public RMIClient init() {
        if (service != null)
            return this;

        try {
            logger.info("sending to " + config.getRMIHost() + ":" + config.getRMIPort()
                    + " using " + config.getRMIService());
            Registry registry = LocateRegistry.getRegistry(config.getRMIHost(), config.getRMIPort());
            // look up the remote object
            service = (CommunicationService) registry.lookup(config.getRMIService());
            return this;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void main(String args[]) throws Exception {
        new RMIClient(new Configuration()).send(Arrays.asList(new Twitter4JTweet(1L, "test", "peter")));
    }

    @Override
    public int send(Collection<? extends Tweet> tweets) throws RemoteException {
        if (service == null)
            return 0;

        return service.send(tweets);
    }
}
