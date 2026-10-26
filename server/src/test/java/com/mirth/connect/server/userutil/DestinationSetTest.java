// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2025 Richard Ogin
// SPDX-FileCopyrightText: 2025-2026 Tony Germano <tony@germano.name>

package com.mirth.connect.server.userutil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.server.Constants;
import com.mirth.connect.userutil.ImmutableConnectorMessage;

public class DestinationSetTest {
    private Set<Integer> createMetadataIds() {
        Set<Integer> metaDataIds = new HashSet<>();
        metaDataIds.add(1);
        metaDataIds.add(3);
        metaDataIds.add(5);
        metaDataIds.add(7);
        metaDataIds.add(9);

        return metaDataIds;
    }

    private Map<String, Integer> createDestinationIdMap() {
        Map<String, Integer> destinationIdMap = new HashMap<>();
        destinationIdMap.put("One", 1);
        destinationIdMap.put("Three", 3);
        destinationIdMap.put("Five", 5);
        destinationIdMap.put("Seven", 7);
        destinationIdMap.put("Nine", 9);

        return destinationIdMap;
    }

    private ImmutableConnectorMessage createMessage(Map<String, Integer> destinationIdMap, Set<Integer> metadataIds) {
        ConnectorMessage cm = new ConnectorMessage();

        if (metadataIds != null) {
            cm.getSourceMap().put(Constants.DESTINATION_SET_KEY, metadataIds);
        }

        if (destinationIdMap != null) {
            return new ImmutableConnectorMessage(cm, true, destinationIdMap);
        } else {
            return new ImmutableConnectorMessage(cm, true);
        }
    }

    /**
     * Runs a JavaScript snippet with destinationSet in scope, as a channel script would. Rhino
     * chooses which overload to call, so these tests check the overloads that scripts reach.
     *
     * @return the result of the script, converted to a boolean
     */
    private boolean evaluate(DestinationSet destinationSet, String script) {
        Context context = Context.enter();
        try {
            Scriptable scope = context.initStandardObjects();
            ScriptableObject.putProperty(scope, "destinationSet", Context.javaToJS(destinationSet, scope));
            return Context.toBoolean(context.evaluateString(scope, script, "test", 1, null));
        } finally {
            Context.exit();
        }
    }

    // --- Tests for remove(Object) ---
    @Test
    public void test_removeObject_withSourceMap_removeForMatchingMetadataId() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(destinationSet.remove(3));
        assertFalse(metaDataIds.contains(3));
        assertTrue(metaDataIds.size() == 4);
    }

    @Test
    public void test_removeObject_withSourceMap_removeForMatchingConnectorName() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(destinationSet.remove("Five"));
        assertFalse(metaDataIds.contains(5));
        assertTrue(metaDataIds.size() == 4);
    }

    @Test
    public void test_removeObject_withSourceMap_noRemovalForNonExistentName() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertFalse(destinationSet.remove("I_DONT_EXIST"));
        assertTrue(metaDataIds.size() == 5);
    }

    @Test
    public void test_removeObject_noSourceMap_noRemoval() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));
        assertFalse(destinationSet.remove(3));
    }

    // --- Tests for remove(Collection) ---
    @Test
    public void test_removeCollection_withSourceMap_removeForMatchingItems() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        Collection<Object> toRemove = Arrays.asList(1, "Three", 99); // "99" does not exist
        assertTrue(destinationSet.remove(toRemove));
        assertFalse(metaDataIds.contains(1));
        assertFalse(metaDataIds.contains(3));
        assertTrue(metaDataIds.size() == 3);
    }
    
    @Test
    public void test_removeCollection_withSourceMap_noRemovalForNonExistentItems() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        Collection<Object> toRemove = Arrays.asList(100, "OneHundred");
        assertFalse(destinationSet.remove(toRemove));
        assertTrue(metaDataIds.size() == 5);
    }

    @Test
    public void test_removeCollection_noSourceMap_noRemoval() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));
        assertFalse(destinationSet.remove(Arrays.asList(1, "Three")));
    }

    // --- Tests for removeAll() ---
    @Test
    public void test_removeAll_withSourceMap_removesAll() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(destinationSet.removeAll());
        assertTrue(metaDataIds.isEmpty());
    }
    
    @Test
    public void test_removeAll_withSourceMap_returnsFalseWhenAlreadyEmpty() throws Exception {
        Set<Integer> metaDataIds = new HashSet<>();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertFalse(destinationSet.removeAll());
    }

    @Test
    public void test_removeAll_noSourceMap_noRemoval() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));
        assertFalse(destinationSet.removeAll());
    }

    // --- Tests for removeAllExcept(Collection) ---
    @Test
    public void test_removeAllExceptCollection_withSourceMap_retainsMatchingItems() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        Collection<Object> toRetain = Arrays.asList(1, "Five", 99); // "99" does not exist
        assertTrue(destinationSet.removeAllExcept(toRetain));
        assertTrue(metaDataIds.size() == 2);
        assertTrue(metaDataIds.contains(1));
        assertTrue(metaDataIds.contains(5));
    }

    @Test
    public void test_removeAllExceptCollection_withSourceMap_removesAllForNonExistentItems() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        Collection<Object> toRetain = Arrays.asList(100, "OneHundred");
        assertTrue(destinationSet.removeAllExcept(toRetain));
        assertTrue(metaDataIds.isEmpty());
    }

    @Test
    public void test_removeAllExceptCollection_noSourceMap_noRemoval() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));
        assertFalse(destinationSet.removeAllExcept(Arrays.asList(1, "Five")));
    }
    
    // --- Tests for removeAllExcept(Object) ---
    @Test
    public void test_removeAllExceptObject_withSourceMap_removeAllForMetadataIdWhichDoesNotExist() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(destinationSet.removeAllExcept("I_DONT_EXIST"));
        assertTrue(metaDataIds.isEmpty());
    }

    @Test
    public void test_removeAllExceptObject_withSourceMap_removeForMatchingMetadataId() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(destinationSet.removeAllExcept(3));
        assertTrue(metaDataIds.size() == 1);
    }

    @Test
    public void test_removeAllExceptObject_withSourceMap_removeForMatchingConnectorName() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(destinationSet.removeAllExcept("Seven"));
        assertTrue(metaDataIds.size() == 1);
    }

    @Test
    public void test_removeAllExceptObject_noSourceMap_noRemovalForMetadataIdWhichDoesNotExist() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));

        assertFalse(destinationSet.removeAllExcept("I_DONT_EXIST"));
    }

    @Test
    public void test_removeAllExceptObject_noSourceMap_noRemovalForMatchingMetadataId() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));

        assertFalse(destinationSet.removeAllExcept(3));
    }

    @Test
    public void test_removeAllExceptObject_noSourceMap_noRemovalForMatchingConnectorName() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));

        assertFalse(destinationSet.removeAllExcept("Seven"));
    }

    // --- Tests without a destination id map ---
    @Test
    public void test_removeObject_noDestinationIdMap_removeForMatchingMetadataId() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(null, metaDataIds));

        assertTrue(destinationSet.remove(3));
        assertFalse(metaDataIds.contains(3));
        assertTrue(metaDataIds.size() == 4);
    }

    @Test
    public void test_removeObject_noDestinationIdMap_noRemovalForConnectorName() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(null, metaDataIds));

        assertFalse(destinationSet.remove("Three"));
        assertTrue(metaDataIds.size() == 5);
    }

    @Test
    public void test_removeAllExceptCollection_noDestinationIdMap_retainsMatchingMetadataIds() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(null, metaDataIds));

        assertTrue(destinationSet.removeAllExcept(Arrays.asList(1, "Five")));
        assertTrue(metaDataIds.size() == 1);
        assertTrue(metaDataIds.contains(1));
    }

    // --- Tests for null collections ---
    @Test(expected = NullPointerException.class)
    public void test_removeCollection_nullCollection_throwsNullPointerException() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), createMetadataIds()));
        destinationSet.remove((Collection<Object>) null);
    }

    @Test(expected = NullPointerException.class)
    public void test_removeAllExceptCollection_nullCollection_throwsNullPointerException() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), createMetadataIds()));
        destinationSet.removeAllExcept((Collection<Object>) null);
    }

    // --- Tests for calls from JavaScript ---
    @Test
    public void test_javascript_removeMetadataId() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(evaluate(destinationSet, "destinationSet.remove(3)"));
        assertTrue(metaDataIds.size() == 4);
        assertFalse(metaDataIds.contains(3));
    }

    @Test
    public void test_javascript_removeConnectorName() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(evaluate(destinationSet, "destinationSet.remove('Three')"));
        assertTrue(metaDataIds.size() == 4);
        assertFalse(metaDataIds.contains(3));
    }

    @Test
    public void test_javascript_removeArray() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(evaluate(destinationSet, "destinationSet.remove([1, 'Three', 99])"));
        assertTrue(metaDataIds.size() == 3);
        assertFalse(metaDataIds.contains(1));
        assertFalse(metaDataIds.contains(3));
    }

    @Test
    public void test_javascript_removeAllExceptConnectorName() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(evaluate(destinationSet, "destinationSet.removeAllExcept('Seven')"));
        assertTrue(metaDataIds.size() == 1);
        assertTrue(metaDataIds.contains(7));
    }

    @Test
    public void test_javascript_removeAllExceptArray() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(evaluate(destinationSet, "destinationSet.removeAllExcept([1, 'Five', 99])"));
        assertTrue(metaDataIds.size() == 2);
        assertTrue(metaDataIds.contains(1));
        assertTrue(metaDataIds.contains(5));
    }

    @Test
    public void test_javascript_removeAll() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(evaluate(destinationSet, "destinationSet.removeAll()"));
        assertTrue(metaDataIds.isEmpty());
    }

    // --- Tests for contains(Object) and containsAll(Collection) ---
    @Test
    public void test_contains_withSourceMap() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), createMetadataIds()));

        assertTrue(destinationSet.contains(3));
        assertTrue(destinationSet.contains(3L));
        assertTrue(destinationSet.contains("Three"));
        assertFalse(destinationSet.contains(2));
        assertFalse(destinationSet.contains("I_DONT_EXIST"));
        assertFalse(destinationSet.contains(null));
    }

    @Test
    public void test_contains_noSourceMap() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));

        assertFalse(destinationSet.contains(3));
        assertFalse(destinationSet.contains("Three"));
    }

    @Test
    public void test_containsAll_withSourceMap() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), createMetadataIds()));

        assertTrue(destinationSet.containsAll(Collections.emptyList()));
        assertTrue(destinationSet.containsAll(Arrays.asList(1, "Three", 5)));
        assertFalse(destinationSet.containsAll(Arrays.asList(1, 2)));
        assertFalse(destinationSet.containsAll(Arrays.asList(1, "I_DONT_EXIST")));
    }

    @Test(expected = NullPointerException.class)
    public void test_containsAll_nullCollection_throwsNullPointerException() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), createMetadataIds()));
        destinationSet.containsAll(null);
    }

    // --- Tests for removeAll(Collection) ---
    @Test
    public void test_removeAllCollection_withSourceMap_removeForMatchingItems() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(destinationSet.removeAll(Arrays.asList(1, "Three", 99)));
        assertEquals(new HashSet<>(Arrays.asList(5, 7, 9)), metaDataIds);
    }

    @Test
    public void test_removeAllCollection_withSourceMap_removesEveryMatchingItem() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(destinationSet.removeAll(Arrays.asList(1, 3, 5, 7)));
        assertEquals(Collections.singleton(9), metaDataIds);
    }

    @Test
    public void test_removeAllCollection_withSourceMap_collectionLargerThanSet() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(destinationSet.removeAll(Arrays.asList(1, "Three", 1.0, 97, 98, 99)));
        assertEquals(new HashSet<>(Arrays.asList(5, 7, 9)), metaDataIds);
    }

    @Test
    public void test_removeAllCollection_withSourceMap_noRemovalForNonExistentItems() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertFalse(destinationSet.removeAll(Arrays.asList(100, "OneHundred")));
        assertTrue(metaDataIds.size() == 5);
    }

    @Test
    public void test_removeAllCollection_noSourceMap_noRemoval() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));
        assertFalse(destinationSet.removeAll(Arrays.asList(1, "Three")));
    }

    @Test(expected = NullPointerException.class)
    public void test_removeAllCollection_nullCollection_throwsNullPointerException() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), createMetadataIds()));
        destinationSet.removeAll((Collection<?>) null);
    }

    // --- Tests for retainAll(Collection) ---
    @Test
    public void test_retainAll_withSourceMap_retainsMatchingItems() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(destinationSet.retainAll(Arrays.asList(1, "Five", 99)));
        assertEquals(new HashSet<>(Arrays.asList(1, 5)), metaDataIds);
    }

    @Test
    public void test_retainAll_withSourceMap_noRemovalWhenAllRetained() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertFalse(destinationSet.retainAll(Arrays.asList(1, "Three", 5, 7, 9)));
        assertTrue(metaDataIds.size() == 5);
    }

    @Test
    public void test_retainAll_noSourceMap_noRemoval() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));
        assertFalse(destinationSet.retainAll(Arrays.asList(1, "Five")));
    }

    @Test(expected = NullPointerException.class)
    public void test_retainAll_nullCollection_throwsNullPointerException() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), createMetadataIds()));
        destinationSet.retainAll(null);
    }

    // --- Tests for add(Integer) and addAll(Collection) ---
    @Test(expected = UnsupportedOperationException.class)
    public void test_add_throwsUnsupportedOperationException() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), createMetadataIds()));
        destinationSet.add(2);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void test_addAll_throwsUnsupportedOperationException() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), createMetadataIds()));
        destinationSet.addAll(Arrays.asList(2, 4));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void test_addAll_emptyCollection_throwsUnsupportedOperationException() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), createMetadataIds()));
        destinationSet.addAll(Collections.emptyList());
    }

    // --- Tests for size(), isEmpty(), and clear() ---
    @Test
    public void test_size_withSourceMap() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertEquals(5, destinationSet.size());
        assertFalse(destinationSet.isEmpty());

        metaDataIds.remove(1);
        assertEquals(4, destinationSet.size());
    }

    @Test
    public void test_size_noSourceMap() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));

        assertEquals(0, destinationSet.size());
        assertTrue(destinationSet.isEmpty());
    }

    @Test
    public void test_clear_withSourceMap_removesAll() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        destinationSet.clear();
        assertTrue(metaDataIds.isEmpty());
        assertTrue(destinationSet.isEmpty());
    }

    @Test
    public void test_clear_noSourceMap() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));

        destinationSet.clear();
        assertTrue(destinationSet.isEmpty());
    }

    // --- Tests for iterator() and toArray() ---
    @Test
    public void test_iterator_withSourceMap_returnsAllMetadataIds() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), createMetadataIds()));

        Set<Integer> iterated = new HashSet<>();
        for (Integer metaDataId : destinationSet) {
            iterated.add(metaDataId);
        }

        assertEquals(createMetadataIds(), iterated);
    }

    @Test
    public void test_iterator_withSourceMap_removeRemovesMetadataId() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        Iterator<Integer> iterator = destinationSet.iterator();
        while (iterator.hasNext()) {
            if (iterator.next() == 3) {
                iterator.remove();
            }
        }

        assertEquals(new HashSet<>(Arrays.asList(1, 5, 7, 9)), metaDataIds);
    }

    @Test
    public void test_removeIf_withSourceMap_removesMatchingMetadataIds() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(destinationSet.removeIf(metaDataId -> metaDataId > 5));
        assertEquals(new HashSet<>(Arrays.asList(1, 3, 5)), metaDataIds);
    }

    @Test
    public void test_iterator_noSourceMap_isEmpty() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), null));
        assertFalse(destinationSet.iterator().hasNext());
    }

    @Test
    public void test_toArray_withSourceMap_returnsAllMetadataIds() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), createMetadataIds()));

        assertEquals(createMetadataIds(), new HashSet<>(Arrays.asList(destinationSet.toArray())));
        assertEquals(createMetadataIds(), new HashSet<>(Arrays.asList(destinationSet.toArray(new Integer[0]))));
    }

    // --- Tests for equals(Object), hashCode(), and toString() ---
    @Test
    public void test_equals_withSourceMap_equalsSetWithSameMetadataIds() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), createMetadataIds()));

        assertEquals(createMetadataIds(), destinationSet);
        assertEquals(destinationSet, createMetadataIds());
        assertEquals(createMetadataIds().hashCode(), destinationSet.hashCode());
        assertNotEquals(destinationSet, new HashSet<>(Arrays.asList(1, 3)));
    }

    @Test
    public void test_toString_withSourceMap_listsMetadataIds() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), new LinkedHashSet<>(Arrays.asList(1, 3, 5))));
        assertEquals("[1, 3, 5]", destinationSet.toString());
    }

    // --- Tests for Set operations called from JavaScript ---
    @Test
    public void test_javascript_contains() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), createMetadataIds()));

        assertTrue(evaluate(destinationSet, "destinationSet.contains(3)"));
        assertTrue(evaluate(destinationSet, "destinationSet.contains('Three')"));
        assertFalse(evaluate(destinationSet, "destinationSet.contains(2)"));
    }

    @Test
    public void test_javascript_removeAllArray() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(evaluate(destinationSet, "destinationSet.removeAll([1, 'Three', 99])"));
        assertEquals(new HashSet<>(Arrays.asList(5, 7, 9)), metaDataIds);
    }

    @Test
    public void test_javascript_removeAllArrayLargerThanSet() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(evaluate(destinationSet, "destinationSet.removeAll([1, 'Three', 97, 98, 99])"));
        assertEquals(new HashSet<>(Arrays.asList(5, 7, 9)), metaDataIds);
    }

    @Test
    public void test_javascript_retainAllArray() throws Exception {
        Set<Integer> metaDataIds = createMetadataIds();
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), metaDataIds));

        assertTrue(evaluate(destinationSet, "destinationSet.retainAll([1, 'Five', 99])"));
        assertEquals(new HashSet<>(Arrays.asList(1, 5)), metaDataIds);
    }

    @Test
    public void test_javascript_add_throwsUnsupportedOperationException() throws Exception {
        DestinationSet destinationSet = new DestinationSet(createMessage(createDestinationIdMap(), createMetadataIds()));

        try {
            evaluate(destinationSet, "destinationSet.add(2)");
        } catch (EvaluatorException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("UnsupportedOperationException"));
            return;
        }

        throw new AssertionError("Expected an exception");
    }
}
