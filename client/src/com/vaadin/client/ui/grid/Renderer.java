/*
 * Copyright 2000-2013 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.client.ui.grid;

/**
 * Renderer for rending a value &lt;T&gt; into cell.
 * <p>
 * You can add a renderer to any column by overring the
 * {@link GridColumn#getRenderer()} method and returning your own renderer. You
 * can retrieve the cell element using {@link Cell#getElement()}.
 * 
 * @param <T>
 *            The column type
 * 
 * @since 7.2
 * @author Vaadin Ltd
 */
public interface Renderer<T> {

    /**
     * Called whenever the {@link Grid} updates a cell
     * 
     * @param cell
     *            The cell that gets updated
     * 
     * @param data
     *            The column data object
     */
    public void renderCell(Cell cell, T data);
}