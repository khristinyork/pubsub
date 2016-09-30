// Copyright 2016 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////
package com.google.pubsub.clients.gcloud;

import com.google.common.base.MoreObjects;
import com.google.common.cache.CacheLoader.InvalidCacheLoadException;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

/**
 * Parameters for this load test run.
 */
class RunParams {
  private static final Logger log = LoggerFactory.getLogger(RunParams.class);
  private static final String PULL_SUBSCRIPTION_POSTFIX = "-sub-pull-%d";
  final RunType runType;
  final String topicName;
  final String subscriptionName;
  final Map<String, String> labels;

  private RunParams(
      RunType runType,
      Map<String, String> labels,
      String topicName,
      String subscriptionName) {
    this.runType = runType;
    this.labels = labels;
    this.topicName = topicName;
    this.subscriptionName = subscriptionName;
  }

  private RunParams(
      RunType runType,
      String topicName) {
    this(runType, LoadTestFlags.labels, topicName, "");
  }

  /**
   * Generates the list of parameters for runs based on the flags passed.
   */
  static List<RunParams> generatePrototypeParams(
      final ObjectRepository objectRepository,
      final ListeningExecutorService executor) {
    boolean useGlobalTopic = LoadTestFlags.numTopics == 0;
    int numTopics = useGlobalTopic ? 1 : LoadTestFlags.numTopics;
    final List<RunParams> protos = Lists.newArrayList();

    Integer maximumInFlight = LoadTestFlags.maxObjectsCreationInflight;
    final Semaphore inFlightLimiter = new Semaphore(maximumInFlight);
    log.info("Max. in-flight creation RPCs: " + maximumInFlight);

    for (int topicNumber = 0; topicNumber < numTopics; topicNumber++) {
      String topicSuffix =
          String.format(LoadTestFlags.perTaskNameSuffixFormat, "topic" + topicNumber);

      final String topicName = LoadTestFlags.loadTestTopicPrefix + (!useGlobalTopic ? topicSuffix : "");
      final String subscriptionPrefixWithTopic =
          LoadTestFlags.loadTestSubscriptionPrefix + (!useGlobalTopic ? topicSuffix : "");

      final List<ListenableFuture<?>> resourceCreationTasks = new ArrayList<>();

      inFlightLimiter.acquireUninterruptibly();
      resourceCreationTasks.add(executor.submit(() -> {
        try {
          objectRepository.createTopic(topicName);
          if (LoadTestFlags.actionIncludesPublish) {
            protos.add(
                new RunParams(RunType.PUBLISH_RUN, topicName));
          }
        } catch (ExecutionException | InvalidCacheLoadException e) {
          log.warn(
              "Failed to generate parameters for " + topicName + " because of object creation problem",
              e);
          return;
        } finally {
          inFlightLimiter.release();
        }

        for (int subscriptionNumber = 0;
             subscriptionNumber < LoadTestFlags.pullFanOutFactor;
             subscriptionNumber++) {
          final String subscriptionName = subscriptionPrefixWithTopic
              + String.format(PULL_SUBSCRIPTION_POSTFIX, subscriptionNumber);
          inFlightLimiter.acquireUninterruptibly();
          resourceCreationTasks.add(executor.submit(() -> {
            try {
              objectRepository.createSubscription(topicName, subscriptionName);
              if (LoadTestFlags.actionIncludesPull) {
                protos.add(new RunParams(
                    RunType.PULL_SUBSCRIPTION_RUN,
                    LoadTestFlags.labels,
                    topicName,
                    subscriptionName));
              }
            } catch (ExecutionException | InvalidCacheLoadException e) {
              log.warn(
                  "Failed to generate parameters for " + topicName + ":" +
                      subscriptionName + " because of object creation problem", e);
            } finally {
              inFlightLimiter.release();
            }
          }));
        }
      }));

      // Wait for all the creations.
      try {
        Futures.allAsList(resourceCreationTasks).get();
      } catch (InterruptedException | ExecutionException e) {
        log.warn("Failed to generate parameters.", e);
      }
    }
    return protos;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("runType", runType.toString())
        .add("labels", labels.toString())
        .add("topicName", topicName)
        .add("subscriptionName", subscriptionName)
        .toString();
  }

  /**
   * Specifies if this is a publish operation, pull subscription operation or push subscription
   * operation.
   */
  private enum RunType {
    PUBLISH_RUN,  // Do a publishing in this run.
    PULL_SUBSCRIPTION_RUN, // Do a subscription pull related action in this run.
  }
}
