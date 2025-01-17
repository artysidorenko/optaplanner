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

import java.util.ArrayDeque;
import java.util.Collection;

import org.optaplanner.core.api.domain.variable.VariableListener;

final class VariableListenerNotifiable<Solution_> implements Comparable<VariableListenerNotifiable<Solution_>> {

    private final VariableListener<Solution_, ?> variableListener;
    private final int globalOrder;

    private final Collection<VariableListenerNotification> notificationQueue;

    public VariableListenerNotifiable(VariableListener<Solution_, ?> variableListener, int globalOrder) {
        this.variableListener = variableListener;
        this.globalOrder = globalOrder;
        if (variableListener.requiresUniqueEntityEvents()) {
            notificationQueue = new SmallScalingOrderedSet<>();
        } else {
            notificationQueue = new ArrayDeque<>();
        }
    }

    public <Entity_> VariableListener<Solution_, Entity_> getVariableListener() {
        return (VariableListener<Solution_, Entity_>) variableListener;
    }

    public int getGlobalOrder() {
        return globalOrder;
    }

    public Collection<VariableListenerNotification> getNotificationQueue() {
        return notificationQueue;
    }

    @Override
    public int compareTo(VariableListenerNotifiable<Solution_> other) {
        if (globalOrder < other.globalOrder) {
            return -1;
        } else if (globalOrder > other.globalOrder) {
            return 1;
        } else {
            return 0;
        }
    }

    @Override
    public String toString() {
        return "(" + globalOrder + ") " + variableListener;
    }

}
