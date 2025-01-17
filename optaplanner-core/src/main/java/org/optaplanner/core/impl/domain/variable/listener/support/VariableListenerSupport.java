/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.core.impl.domain.variable.listener.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.variable.VariableListener;
import org.optaplanner.core.api.score.director.ScoreDirector;
import org.optaplanner.core.impl.domain.entity.descriptor.EntityDescriptor;
import org.optaplanner.core.impl.domain.solution.descriptor.SolutionDescriptor;
import org.optaplanner.core.impl.domain.variable.descriptor.GenuineVariableDescriptor;
import org.optaplanner.core.impl.domain.variable.descriptor.ShadowVariableDescriptor;
import org.optaplanner.core.impl.domain.variable.descriptor.VariableDescriptor;
import org.optaplanner.core.impl.domain.variable.listener.SourcedVariableListener;
import org.optaplanner.core.impl.domain.variable.supply.Demand;
import org.optaplanner.core.impl.domain.variable.supply.Supply;
import org.optaplanner.core.impl.domain.variable.supply.SupplyManager;
import org.optaplanner.core.impl.score.director.InnerScoreDirector;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public final class VariableListenerSupport<Solution_> implements SupplyManager<Solution_> {

    private final InnerScoreDirector<Solution_, ?> scoreDirector;

    private final List<VariableListenerNotifiable<Solution_>> notifiableList;
    private final Map<VariableDescriptor<Solution_>, List<VariableListenerNotifiable<Solution_>>> sourceVariableToNotifiableMap;
    private final Map<EntityDescriptor<Solution_>, List<VariableListenerNotifiable<Solution_>>> sourceEntityToNotifiableMap;
    private final Map<Demand<Solution_, ?>, Supply> supplyMap;
    private int nextGlobalOrder = 0;

    private boolean notificationQueuesAreEmpty;

    public VariableListenerSupport(InnerScoreDirector<Solution_, ?> scoreDirector) {
        this.scoreDirector = scoreDirector;
        notifiableList = new ArrayList<>();
        sourceVariableToNotifiableMap = new LinkedHashMap<>();
        sourceEntityToNotifiableMap = new LinkedHashMap<>();
        supplyMap = new LinkedHashMap<>();
    }

    public void linkVariableListeners() {
        notificationQueuesAreEmpty = true;
        for (EntityDescriptor<Solution_> entityDescriptor : scoreDirector.getSolutionDescriptor().getEntityDescriptors()) {
            for (VariableDescriptor<Solution_> variableDescriptor : entityDescriptor.getDeclaredVariableDescriptors()) {
                sourceVariableToNotifiableMap.put(variableDescriptor, new ArrayList<>());
            }
            sourceEntityToNotifiableMap.put(entityDescriptor, new ArrayList<>());
        }
        for (EntityDescriptor<Solution_> entityDescriptor : scoreDirector.getSolutionDescriptor().getEntityDescriptors()) {
            for (ShadowVariableDescriptor<Solution_> shadowVariableDescriptor : entityDescriptor
                    .getDeclaredShadowVariableDescriptors()) {
                if (shadowVariableDescriptor.hasVariableListener(scoreDirector)) {
                    VariableListener<Solution_, ?> variableListener =
                            shadowVariableDescriptor.buildVariableListener(scoreDirector);
                    if (variableListener instanceof Supply) {
                        // Non-sourced variable listeners (ie. ones provided by the user) can never be a supply.
                        supplyMap.put(shadowVariableDescriptor.getProvidedDemand(), (Supply) variableListener);
                    }
                    int globalOrder = shadowVariableDescriptor.getGlobalShadowOrder();
                    if (nextGlobalOrder <= globalOrder) {
                        nextGlobalOrder = globalOrder + 1;
                    }
                    VariableListenerNotifiable<Solution_> notifiable =
                            new VariableListenerNotifiable<>(variableListener, globalOrder);
                    for (VariableDescriptor<Solution_> source : shadowVariableDescriptor.getSourceVariableDescriptorList()) {
                        List<VariableListenerNotifiable<Solution_>> variableNotifiableList =
                                sourceVariableToNotifiableMap.get(source);
                        variableNotifiableList.add(notifiable);
                        List<VariableListenerNotifiable<Solution_>> entityNotifiableList = sourceEntityToNotifiableMap
                                .get(source.getEntityDescriptor());
                        if (!entityNotifiableList.contains(notifiable)) {
                            entityNotifiableList.add(notifiable);
                        }
                    }
                    notifiableList.add(notifiable);
                }
            }
        }
        Collections.sort(notifiableList);
    }

    @Override
    public <Supply_ extends Supply> Supply_ demand(Demand<Solution_, Supply_> demand) {
        Supply_ supply = (Supply_) supplyMap.get(demand);
        if (supply == null) {
            supply = demand.createExternalizedSupply(scoreDirector);
            if (supply instanceof SourcedVariableListener) {
                SourcedVariableListener<Solution_, ?> variableListener =
                        (SourcedVariableListener<Solution_, ?>) supply;
                // An external ScoreDirector can be created before the working solution is set
                if (scoreDirector.getWorkingSolution() != null) {
                    variableListener.resetWorkingSolution(scoreDirector);
                }
                VariableDescriptor<Solution_> source = variableListener.getSourceVariableDescriptor();
                VariableListenerNotifiable<Solution_> notifiable =
                        new VariableListenerNotifiable<>(variableListener, nextGlobalOrder);
                nextGlobalOrder++;
                List<VariableListenerNotifiable<Solution_>> variableNotifiableList = sourceVariableToNotifiableMap.get(source);
                variableNotifiableList.add(notifiable);
                List<VariableListenerNotifiable<Solution_>> entityNotifiableList = sourceEntityToNotifiableMap
                        .get(source.getEntityDescriptor());
                if (!entityNotifiableList.contains(notifiable)) {
                    entityNotifiableList.add(notifiable);
                }
                notifiableList.add(notifiable);
                // No need to sort notifiableList again because notifiable's globalOrder is highest
            }
            supplyMap.put(demand, supply);
        }
        return supply;
    }

    // ************************************************************************
    // Lifecycle methods
    // ************************************************************************

    public void resetWorkingSolution() {
        for (VariableListenerNotifiable<Solution_> notifiable : notifiableList) {
            VariableListener<Solution_, ?> variableListener = notifiable.getVariableListener();
            variableListener.resetWorkingSolution(scoreDirector);
        }
    }

    public void clearWorkingSolution() {
        for (VariableListenerNotifiable<Solution_> notifiable : notifiableList) {
            VariableListener<Solution_, ?> variableListener = notifiable.getVariableListener();
            variableListener.close();
        }
    }

    public void beforeEntityAdded(EntityDescriptor<Solution_> entityDescriptor, Object entity) {
        List<VariableListenerNotifiable<Solution_>> notifiableList = sourceEntityToNotifiableMap.get(entityDescriptor);
        if (!notifiableList.isEmpty()) {
            VariableListenerNotification notification =
                    new VariableListenerNotification(entity, VariableListenerNotificationType.ENTITY_ADDED);
            for (VariableListenerNotifiable<Solution_> notifiable : notifiableList) {
                Collection<VariableListenerNotification> notificationQueue = notifiable.getNotificationQueue();
                boolean added = notificationQueue.add(notification);
                if (added) {
                    notifiable.getVariableListener().beforeEntityAdded(scoreDirector, entity);
                }
            }
        }
        notificationQueuesAreEmpty = false;
    }

    public void afterEntityAdded(EntityDescriptor<Solution_> entityDescriptor, Object entity) {
        // beforeEntityAdded() has already added it to the notificationQueue
    }

    public void beforeVariableChanged(VariableDescriptor<Solution_> variableDescriptor, Object entity) {
        List<VariableListenerNotifiable<Solution_>> notifiableList =
                sourceVariableToNotifiableMap.getOrDefault(variableDescriptor,
                        Collections.emptyList()); // Avoids null for chained swap move on an unchained var.
        if (!notifiableList.isEmpty()) {
            VariableListenerNotification notification =
                    new VariableListenerNotification(entity, VariableListenerNotificationType.VARIABLE_CHANGED);
            for (VariableListenerNotifiable<Solution_> notifiable : notifiableList) {
                Collection<VariableListenerNotification> notificationQueue = notifiable.getNotificationQueue();
                boolean added = notificationQueue.add(notification);
                if (added) {
                    notifiable.getVariableListener().beforeVariableChanged(scoreDirector, entity);
                }
            }
        }
        notificationQueuesAreEmpty = false;
    }

    public void afterVariableChanged(VariableDescriptor<Solution_> variableDescriptor, Object entity) {
        // beforeVariableChanged() has already added it to the notificationQueue
    }

    public void beforeEntityRemoved(EntityDescriptor<Solution_> entityDescriptor, Object entity) {
        List<VariableListenerNotifiable<Solution_>> notifiableList = sourceEntityToNotifiableMap.get(entityDescriptor);
        if (!notifiableList.isEmpty()) {
            VariableListenerNotification notification =
                    new VariableListenerNotification(entity, VariableListenerNotificationType.ENTITY_REMOVED);
            for (VariableListenerNotifiable<Solution_> notifiable : notifiableList) {
                Collection<VariableListenerNotification> notificationQueue = notifiable.getNotificationQueue();
                boolean added = notificationQueue.add(notification);
                if (added) {
                    notifiable.getVariableListener().beforeEntityRemoved(scoreDirector, entity);
                }
            }
        }
        notificationQueuesAreEmpty = false;
    }

    public void afterEntityRemoved(EntityDescriptor<Solution_> entityDescriptor, Object entity) {
        // beforeEntityRemoved() has already added it to the notificationQueue
    }

    public void triggerVariableListenersInNotificationQueues() {
        for (VariableListenerNotifiable<Solution_> notifiable : notifiableList) {
            Collection<VariableListenerNotification> notificationQueue = notifiable.getNotificationQueue();
            int notifiedCount = 0;
            VariableListener<Solution_, Object> variableListener = notifiable.getVariableListener();
            for (VariableListenerNotification notification : notificationQueue) {
                Object entity = notification.getEntity();
                switch (notification.getType()) {
                    case ENTITY_ADDED:
                        variableListener.afterEntityAdded(scoreDirector, entity);
                        break;
                    case VARIABLE_CHANGED:
                        variableListener.afterVariableChanged(scoreDirector, entity);
                        break;
                    case ENTITY_REMOVED:
                        variableListener.afterEntityRemoved(scoreDirector, entity);
                        break;
                    default:
                        throw new IllegalStateException("The variableListenerNotificationType ("
                                + notification.getType() + ") is not implemented.");
                }
                notifiedCount++;
            }
            if (notifiedCount != notificationQueue.size()) {
                throw new IllegalStateException("The variableListener (" + variableListener.getClass()
                        + ") has been notified with notifiedCount (" + notifiedCount
                        + ") but after notification it has different size (" + notificationQueue.size() + ").\n"
                        + "Maybe that variableListener (" + variableListener.getClass()
                        + ") changed an upstream shadow variable (which is illegal).");
            }
            notificationQueue.clear();
        }
        notificationQueuesAreEmpty = true;
    }

    public void triggerAllVariableListeners() {
        SolutionDescriptor<Solution_> solutionDescriptor = scoreDirector.getSolutionDescriptor();
        List<Object> entityList = scoreDirector.getWorkingEntityList();
        for (Object entity : entityList) {
            EntityDescriptor<Solution_> entityDescriptor = solutionDescriptor.findEntityDescriptorOrFail(entity.getClass());
            for (GenuineVariableDescriptor<Solution_> variableDescriptor : entityDescriptor
                    .getGenuineVariableDescriptorList()) {
                beforeVariableChanged(variableDescriptor, entity);
                // No change
                afterVariableChanged(variableDescriptor, entity);
            }
        }
        triggerVariableListenersInNotificationQueues();
    }

    public void assertNotificationQueuesAreEmpty() {
        if (!notificationQueuesAreEmpty) {
            throw new IllegalStateException("The notificationQueues might not be empty (" + notificationQueuesAreEmpty
                    + ") so any shadow variables might be stale so score calculation is unreliable.\n"
                    + "Maybe a " + ScoreDirector.class.getSimpleName() + ".before*() method was called"
                    + " without calling " + ScoreDirector.class.getSimpleName() + ".triggerVariableListeners(),"
                    + " before calling " + ScoreDirector.class.getSimpleName() + ".calculateScore().");
        }
    }
}
