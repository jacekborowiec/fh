package pl.fhframework.model.forms.optimized;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import pl.fhframework.BindingResult;
import pl.fhframework.annotations.*;
import pl.fhframework.binding.*;
import pl.fhframework.core.FhBindingException;
import pl.fhframework.core.FhException;
import pl.fhframework.core.forms.IHasBoundableLabel;
import pl.fhframework.core.logging.FhLogger;
import pl.fhframework.core.util.CollectionsUtils;
import pl.fhframework.events.IEventSource;
import pl.fhframework.events.IEventSourceContainer;
import pl.fhframework.model.dto.ElementChanges;
import pl.fhframework.model.dto.InMessageEventData;
import pl.fhframework.model.dto.ValueChange;
import pl.fhframework.model.forms.Iterator;
import pl.fhframework.model.forms.*;
import pl.fhframework.model.forms.attribute.RowHeight;
import pl.fhframework.model.forms.attribute.TableGrid;
import pl.fhframework.model.forms.attribute.TableStripes;
import pl.fhframework.model.forms.designer.BindingExpressionDesignerPreviewProvider;
import pl.fhframework.model.forms.designer.IDesignerEventListener;
import pl.fhframework.model.forms.table.LowLevelRowMetadata;
import pl.fhframework.model.forms.table.RowIteratorMetadata;
import pl.fhframework.model.forms.TableRowOptimized;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static pl.fhframework.annotations.DesignerXMLProperty.PropertyFunctionalArea.*;

/**
 * Table is component which describes and operates on tabular data. Table consists of TableRowOprimized and
 * ColumnOptimized components, handles user actions as well as binds model data to its elements.<br/>
 * Attributes:<br/>
 * <pre>
 * <code>collection</code> - table model
 * <code>selected</code> - selected row
 * <code>iterator</code> - bind variable used in ColumnOptimized components
 * <code>onRowClick</code> - server side action executed when table row is chosen
 * </pre>
 * Example
 * <pre>{@code
 * rowList::= class Car {id, name}
 * <Table iterator="item" onRowClick="-" selected="{selectedElement}" collection="{rowList}">
 *  <ColumnOptimized label="Lp" value="{item.id}"/>
 *  <ColumnOptimized label="Name" value="{item.name}"/>
 * </Table>}</pre>
 */
@Control(parents = {Tab.class, GroupingComponent.class, Row.class, Form.class, Repeater.class}, invalidParents = {TableOptimized.class}, canBeDesigned = true)
@DocumentedComponent(value = "TableOptimized", icon = "fa fa-table")
public class TableOptimized extends  Repeater implements ITabular, IChangeableByClient, IEventSourceContainer, IRowNumberOffsetSupplier, Boundable, CompactLayout, IDesignerEventListener, IHasBoundableLabel {

    protected static final String LABEL_ATTR = "label";

    @Getter
    @Setter
    @XMLProperty
    @DocumentedComponentAttribute(defaultValue = "false", value = "Determines if multiselect is enabled in table. If multiselect is set to true, selectedElement has to be set to Collection.")
    @DesignerXMLProperty(functionalArea = SPECIFIC, priority = 10)
    private boolean multiselect;

    @Getter
    @Setter
    @XMLProperty
    @DocumentedComponentAttribute(defaultValue = "false", value = "Determines if horizontal scrolling is enabled in table.")
    @DesignerXMLProperty(functionalArea = LOOK_AND_STYLE, priority = 68)
    private boolean horizontalScrolling;

    @Getter
    @Setter
    @XMLProperty
    @DocumentedComponentAttribute(value = "Allows row selection using checkbox. Works only if multiselect is also enabled.")
    @DesignerXMLProperty(functionalArea = SPECIFIC, priority = 5)
    private boolean selectionCheckboxes;

    @Getter
    @Setter
    @XMLProperty
    @DocumentedComponentAttribute(value = "Enables row selection feature.")
    @DesignerXMLProperty(functionalArea = SPECIFIC, priority = 15)
    private boolean selectable;

    @Getter
    @Setter
    @XMLProperty
    @DocumentedComponentAttribute(value = "Enables fixed headers when scrolling. Works only if table height is set.")
    @DesignerXMLProperty(functionalArea = SPECIFIC, priority = 15)
    protected boolean fixedHeader;

    @JsonIgnore
    @Getter
    @Setter
    @XMLProperty(SELECTED)
    @DocumentedComponentAttribute(boundable = true, value = "Selected table row")
    @DesignerXMLProperty(functionalArea = CONTENT, priority = 11, bindingOnly = true)
    protected ModelBinding selectedElementBinding;

    @Getter
//    @JsonView(IWithSubelements.class)
    protected List<TableRowOptimized> tableRows = new ArrayList<>();

    @Getter
    @Setter
    @XMLMetadataSubelement
    protected Footer footer;

    @JsonIgnore
    protected List<LowLevelRowMetadata> tableRowMetadata = new ArrayList<>();

    @JsonIgnore
    protected boolean tableRowMetadataChanged = false;

    @Getter
    @Setter
    protected int displayedRowsCount;

    @Getter
    @XMLProperty(defaultValue = "-")
    @DocumentedComponentAttribute(value = "If the table row is clicked that method will be executed")
    @DesignerXMLProperty(functionalArea = BEHAVIOR)
    private ActionBinding onRowClick;

    @Getter
    @XMLProperty(defaultValue = "-")
    @DocumentedComponentAttribute(value = "If the table row is clicked twice that method will be executed")
    @DesignerXMLProperty(functionalArea = BEHAVIOR)
    private ActionBinding onRowDoubleClick;

    @Getter
    protected int[] selectedRowsNumbers = new int[]{-1};

    @JsonIgnore
    protected Collection mainCollection;

    @JsonIgnore
    @Getter
    @Setter
    @XMLMetadataSubelements
    private List<Iterator> iterators = new LinkedList<>();

    @JsonIgnore
    private List<Iterator> allIterators;

    @Getter
    @Setter
    protected Map<Integer, Integer> rowIndexMappings = null;

    @JsonIgnore
    protected boolean rowIndexMappingsChanged = false;

    @Getter
    @Setter
    @XMLProperty
    @DocumentedComponentAttribute(defaultValue = "false", value = "Enables IE inline elements focus fix. Attribute is ignored in non-IE browsers. If this attribute is set to true, spanning columns doesn't work.")
    @DesignerXMLProperty(functionalArea = SPECIFIC, priority = 79)
    protected boolean ieFocusFixEnabled;

    @Getter
    @Setter
    @XMLProperty
    @DocumentedComponentAttribute(value = "Minimum of displayed rows.")
    @DesignerXMLProperty(functionalArea = LOOK_AND_STYLE, priority = 73)
    private Integer minRows;

    @Getter
    @Setter
    @XMLProperty
    @DocumentedComponentAttribute(defaultValue = "normal", value = "Sets row height on tables")
    @DesignerXMLProperty(functionalArea = LOOK_AND_STYLE, priority = 72)
    private RowHeight rowHeight;

    @Getter
    @Setter
    @XMLProperty
    @DocumentedComponentAttribute(defaultValue = "show", value = "Displays or hides grid on tables")
    @DesignerXMLProperty(functionalArea = LOOK_AND_STYLE, priority = 71)
    private TableGrid tableGrid;

    @Getter
    @Setter
    @XMLProperty
    @DocumentedComponentAttribute(defaultValue = "show", value = "Displays or hides gray stripes on table rows")
    @DesignerXMLProperty(functionalArea = LOOK_AND_STYLE, priority = 70)
    private TableStripes tableStripes;


    @Getter
    @XMLMetadataSubelements
    private List<ColumnOptimized> columns = new ArrayList<>();

    protected static final String SELECTION_CHECKBOXES = "selectionCheckboxes";

    @JsonIgnore
    @Getter
    @Setter
    @XMLProperty
    @DesignerXMLProperty(allowedTypes = Map.class, functionalArea = LOOK_AND_STYLE, priority = 69)
    @DocumentedComponentAttribute(value = "Map of colored rows in pattern like <BusinessObject, Color>", boundable = true)
    protected ModelBinding<Map> rowStylesMap; // bacause of java code generation problem Map<?, String> cannot be used

    @Getter
    protected Map<Integer, String> rowStylesMapping = new HashMap<>();

    @JsonIgnore
    protected boolean rowStylesChanged = true;

    @Getter
    private String label = "";

    @JsonIgnore
    @Getter
    @Setter
    @XMLProperty(value = LABEL_ATTR)
    @DesignerXMLProperty(commonUse = true, previewValueProvider = BindingExpressionDesignerPreviewProvider.class, priority = 100, functionalArea = CONTENT)
    @DocumentedComponentAttribute(boundable = true, value = "Represents label for created component. Supports FHML - Fh Markup Language.")
    private ModelBinding labelModelBinding;

    @Getter
    @Setter
    @XMLProperty
    @DocumentedComponentAttribute(defaultValue = "", value = "How much records load on start.")
    @DesignerXMLProperty(functionalArea = SPECIFIC, priority = 68)
    private Integer startSize;

    private static final String ON_ROW_CLICK = "onRowClick";

    private static final String ON_ROW_DOUBLE_CLICK = "onRowDoubleClick";

    private static final String DISPLAYED_ROWS_COUNT = "displayedRowsCount";

    private static final String TABLE_ROWS = "tableRows";

    private static final String SELECTED_ROW_NUMBER = "selectedRowNumber";

    private static final String SELECTED = "selected";

    protected static final String MULTISELECT = "multiselect";

    private static final String ROW_INDEX_MAPPINGS = "rowIndexMappings";

    private static final String MIN_ROWS = "minRows";

    private static final String ROW_STYLES_MAP = "rowStylesMap";

    private static final String ROW_STYLES_MAPPING = "rowStylesMapping";

    public TableOptimized(Form form) {
        super(form);
    }

    public void init() {
        super.init();
        setProcessComponentStateChange(false);
    }

    @Override
    public int getRowNumberOffset() {
        return 0;
    }

    @Override
    public void doActionForEverySubcomponent(Consumer<Component> action) {
        for (ColumnOptimized column : getColumns()) {
            action.accept(column);
            column.doActionForEverySubcomponent((Consumer) action);
        }

        for (TableRowOptimized row : tableRows) {
            for (FormElement cell : row.getTableCells()) {
                action.accept(cell);
                if (cell instanceof IGroupingComponent) {
                    ((IGroupingComponent<Component>) cell).doActionForEverySubcomponent(action);
                }
            }
        }

        if (this.footer != null) {
            footer.doActionForEverySubcomponent(action);
        }
    }

    @Override
    public void processComponents() {
        if (getForm().getViewMode() == Form.ViewMode.NORMAL) {
            // build low level rows
            List<LowLevelRowMetadata> newTableRowMetadata = new ArrayList<>();
            if (getCollection() != null) {
                createIteratorStructureRows(newTableRowMetadata, // rows will by added here
                        getAllIterators().subList(1, getAllIterators().size()), // other than main iterator
                        new LinkedHashMap<>(), // empty context so far
                        new int[0], // parent indices - no parents at this point
                        getAllIterators().get(0)); // main iterator

                if (selectedElementBinding != null && !isMultiselect()) { // FH-4184
                    BindingResult bindingResult = selectedElementBinding.getBindingResult();
                    if (bindingResult != null) {
                        Object value = bindingResult.getValue();
                        if (value != null && !getBindedObjectsList().contains(value)) {
                            this.updateBindingForValue(null, selectedElementBinding, selectedElementBinding.getBindingExpression());
                        }
                    }
                }
            }

            Map<?, String> rowStylesValues = extractRowStylesMapping(this.rowStylesMap);
            rowStylesMapping = new HashMap<>();
            for (LowLevelRowMetadata rowMetadata : newTableRowMetadata) {
                int mainIteratorIdx = rowMetadata.getIteratorData().get(getIterator()).getIndex();

                if (rowStylesValues != null) {
                    String rowStyle = rowStylesValues.get(rowMetadata.getIteratorData().get(getIterator()).getBusinessObject());
                    rowStylesMapping.put(mainIteratorIdx, rowStyle);
                }

                this.rowStylesChanged = true;
            }

            // this is a deep equals invocation - see TableIteratorsTests.testRowStructureEquals() test
            if (this.tableRowMetadata.equals(newTableRowMetadata)) {
                // deep structure of table has not changed
                // only need to calculate rowspan for columns
                for (ColumnOptimized column : getColumns()) {
                    calculateRowspan(column);
                }
                return;
            }

            setProcessComponentStateChange(false);
            getBindedSubcomponents().clear();

            List<TableRowOptimized> newTableRows = new ArrayList<>();
            if (getCollection() != null) {
                for (LowLevelRowMetadata rowMetadata : newTableRowMetadata) {
                    newTableRows.add(new TableRowOptimized(this, rowMetadata));
                }
            }

            if (getAllIterators().size() > 1) {
                rowIndexMappings = new HashMap<>();
                int rowIdx = 0;

                for (LowLevelRowMetadata rowMetadata : newTableRowMetadata) {
                    int mainIteratorIdx = rowMetadata.getIteratorData().get(getIterator()).getIndex();
                    rowIndexMappings.put(rowIdx++, mainIteratorIdx);
                }

                rowIndexMappingsChanged = true;
            }

            this.tableRows = newTableRows;
            this.tableRowMetadata = newTableRowMetadata;
            this.tableRowMetadataChanged = true;
        } else if (getForm().getViewMode() == Form.ViewMode.PREVIEW) {
            for (ColumnOptimized column : getColumns()) {
                calculateRowspan(column);
            }
            setProcessComponentStateChange(false);
            getBindedSubcomponents().clear();

            List<TableRowOptimized> newTableRows = new ArrayList<>();
            newTableRows.add(new TableRowOptimized(this, null));

            if (this.columns.stream().noneMatch(c -> c.getValue() != null)) {
                OutputLabel emptyLabel = new OutputLabel(this.getForm());
                emptyLabel.setBody("&nbsp;");
                emptyLabel.setId("EmptyLabel1");

                TableRowOptimized newTableRow = newTableRows.get(0);
                newTableRow.getTableCells().set(0, emptyLabel);
            }

            this.tableRows = newTableRows;
            this.tableRowMetadataChanged = true;
        } else if (getForm().getViewMode() == Form.ViewMode.DESIGN) {
            for (ColumnOptimized column : getColumns()) {
                calculateRowspan(column);
            }
        }
    }

    protected void calculateRowspan(ColumnOptimized column) {
        List<Component> subColumns = column.getSubcomponents().stream().filter(subComponent -> subComponent instanceof ColumnOptimized).collect(Collectors.toList());
        if (subColumns.size() == 0) {
            column.setRowspan(column.getMaxColumnDepthForLevel(column.getLevel(), this.getColumns()));
        } else {
            List<ColumnOptimized> sub = new ArrayList(subColumns);
            for (ColumnOptimized subColumn : sub) {
                calculateRowspan(subColumn);

            }
        }
    }

    protected Map<?, String> extractRowStylesMapping(ModelBinding<Map> rowStylesMap) {
        if (rowStylesMap != null) {
            BindingResult<Map> bindingResult = rowStylesMap.getBindingResult();
            Object bindingResultObj = bindingResult != null ? bindingResult.getValue() : null;
            if (bindingResultObj != null) {
                if (bindingResultObj instanceof Map) {
                    return (Map<?, String>) bindingResultObj;
                } else {
                    throw new FhException("Not instance of Map: " + rowStylesMap);
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    protected Collection extractCollection(IndexedModelBinding collectionBinding, int[] parentIteratorIndices, boolean isMainLevel) {
        Object bindingResultObj = collectionBinding.getValue(parentIteratorIndices);
        if (bindingResultObj != null) {
            if (bindingResultObj instanceof Collection) {
                Collection collectionObj = (Collection) bindingResultObj;
                if (isMainLevel) {
                    this.mainCollection = collectionObj;
                }
                return collectionObj;
            } else {
                throw new FhBindingException("Not instance of Collection: " + collectionBinding.getBindingExpression());
            }
        } else {
            return null;
        }
    }

    protected void createIteratorStructureRows(List<LowLevelRowMetadata> alreadyAddedRows,
                                               List<Iterator> childenIterators,
                                               Map<String, RowIteratorMetadata> parentContext,
                                               int[] parentIndices,
                                               Iterator myIterator) {
        boolean isMainLevel = parentContext.isEmpty();
        Collection myCollection = extractCollection(myIterator.getCollection(), parentIndices, isMainLevel);
        if (myCollection == null || myCollection.isEmpty()) {
            if (FhLogger.isTraceEnabled(Table.class)) {
                FhLogger.trace(this.getClass(), logger -> logger.log("nodata for iterator: {}", myIterator.getId()));
            }
            if (!isMainLevel) {
                addLowLevelRow(alreadyAddedRows, parentContext, null, null);
            }
            return;
        }

        int rowIndex = 0;
        int[] myIndices = Arrays.copyOf(parentIndices, parentIndices.length + 1);
        for (Object myBusinessObject : myCollection) {
            myIndices[myIndices.length - 1] = rowIndex;

            // my rowIndex > 0 - remove parent iterators' firstOccurence flag
            if (rowIndex > 0) {
                for (RowIteratorMetadata parentIteratorData : parentContext.values()) {
                    parentIteratorData.setFirstOccurrence(false);
                }
            }

            RowIteratorMetadata myMetadata = new RowIteratorMetadata();
            myMetadata.setIndex(rowIndex);
            myMetadata.setFirstOccurrence(true);
            myMetadata.setBusinessObject(myBusinessObject);
            myMetadata.setRowSpan(new AtomicInteger()); // will be set later
            int rowsBeforeAddingChildren = alreadyAddedRows.size();

            // we are a last node in the tree - add leaf element
            if (childenIterators.isEmpty()) {
                addLowLevelRow(alreadyAddedRows, parentContext, myIterator.getId(), myMetadata);
            } else {
                // we are an intermediate node in the tree - delegate adding to lower child iterators
                parentContext.put(myIterator.getId(), myMetadata);

                Iterator childIterator = childenIterators.get(0);
                createIteratorStructureRows(alreadyAddedRows,
                        childenIterators.subList(1, childenIterators.size()),
                        parentContext,
                        myIndices,
                        childIterator);

                parentContext.remove(myIterator.getId());
            }

            // AtomicInteger instance of this parent is shared in all children - setting value here will propagate to children
            myMetadata.getRowSpan().set(alreadyAddedRows.size() - rowsBeforeAddingChildren);
            rowIndex++;
        }
    }

    protected void addLowLevelRow(List<LowLevelRowMetadata> alreadyAddedRows,
                                  Map<String, RowIteratorMetadata> context,
                                  String iterator,
                                  RowIteratorMetadata iteratorMetadata) {
        LowLevelRowMetadata row = new LowLevelRowMetadata();
        // copy current parent iterators' data
        context.forEach((iter, data) -> {
            RowIteratorMetadata dataCopy = data.getCopy();
            row.getIteratorData().put(iter, dataCopy);
        });
        // add current iterator's data
        if (iterator != null) {
            row.getIteratorData().put(iterator, iteratorMetadata);
        }
        row.setIteratorsIndices(CollectionsUtils.toArray(
                row.getIteratorData().values().stream()
                        .map(data -> data.getIndex())
                        .collect(Collectors.toList())));
        alreadyAddedRows.add(row);
    }

    @Override
    public void refreshView(Set<ElementChanges> changeSet) {
        ElementChanges elementChanges = this.updateView();

        IGroupingComponent parent = getGroupingParentComponent();
        boolean stopProcessingUpdateView = isStopProcessingUpdateView();
        while (parent != null) {
            stopProcessingUpdateView |= ((FormElement) parent).isStopProcessingUpdateView();
            parent = ((FormElement) parent).getGroupingParentComponent();
        }

        if (!stopProcessingUpdateView && elementChanges.containsAnyChanges()) {
            changeSet.add(elementChanges);
        }
    }

    @Override
    protected ElementChanges updateView() {
        ElementChanges elementChange = super.updateView();

        if (tableRows != null) {
            if (tableRowMetadataChanged) {
                this.displayedRowsCount = tableRows.size();
                elementChange.addChange(DISPLAYED_ROWS_COUNT, this.displayedRowsCount);
                elementChange.addChange(TABLE_ROWS, this.tableRows);
                tableRowMetadataChanged = false;
            }
            if (rowIndexMappingsChanged) {
                elementChange.addChange(ROW_INDEX_MAPPINGS, this.rowIndexMappings);
                rowIndexMappingsChanged = false;
            }

            if (rowStylesChanged) {
                elementChange.addChange(ROW_STYLES_MAPPING, this.rowStylesMapping);
                rowStylesChanged = false;
            }

            this.selectedRowsNumbers = getSelectedRowNumberBasedOnBinding(mainCollection, this.multiselect);
            elementChange.addChange(SELECTED_ROW_NUMBER, this.selectedRowsNumbers);
            refreshView();
        }

        processLabelBinding(elementChange);

        // przeniesienie stylów kolumny na komórki
        IntStream.range(0, columns.size()).forEach(i -> {
            String columnStyles = columns.get(i).getStyleClasses();
            if (columnStyles != null) {
                for (TableRowOptimized row : this.tableRows) {
                    row.getTableCells().get(i).setStyleClasses(columnStyles);
                }
            }
        });

        return elementChange;
    }

    public ActionBinding getRowBinding(ActionBinding binding, Component clonedComponent, Map<String, String> iteratorReplacements) {
        if (binding == null) {
            return binding;
        } else {
            return new AdHocActionBinding(getRowBinding(binding.getActionBindingExpression(), iteratorReplacements, false), getForm(), clonedComponent);
        }
    }

    public ModelBinding getRowBinding(ModelBinding binding, Component clonedComponent, Map<String, String> iteratorReplacements) {
        if (binding == null || binding instanceof StaticBinding) {
            return binding;
        } else {
            return new AdHocModelBinding<>(getForm(), clonedComponent, getRowBinding(binding.getBindingExpression(), iteratorReplacements, true));
        }
    }

    String getRowBinding(String binding, Map<String, String> iteratorReplacements) {
        return getRowBinding(binding, iteratorReplacements, true);
    }

    String getRowBinding(String binding, Map<String, String> iteratorReplacements, boolean useCurlyBrackets) {
        return AdHocIndexedModelBinding.replaceIteratorsInBinding(binding, iteratorReplacements, useCurlyBrackets);
    }

    String replaceBinding(String binding, String key, String replacement) {
        return binding.replace(key, replacement);
    }

    protected int[] getSelectedRowNumberBasedOnBinding(Collection collection, boolean multiselect) {
        Object newSelectedValue = null;
        if (selectedElementBinding != null) {
            BindingResult bindingResult = selectedElementBinding.getBindingResult();
            if (bindingResult != null) {
                newSelectedValue = bindingResult.getValue();
            }
        }
        if (newSelectedValue == null || collection == null) {
            return new int[]{-1};
        }
        if (multiselect) {
            if (newSelectedValue instanceof Collection) {
                List<?> tempCollection = new LinkedList<>(collection);
                List<?> newSelectedElementsList = new LinkedList<>((Collection) newSelectedValue);
                int[] newSelectedRows = new int[newSelectedElementsList.size()];
                for (int i = 0; i < ((Collection) newSelectedValue).size(); i++) {
                    newSelectedRows[i] = tempCollection.indexOf(newSelectedElementsList.get(i));
                }
                return newSelectedRows;
            } else {
                return new int[]{-1};
            }
        } else {
            return new int[]{new LinkedList(collection).indexOf(newSelectedValue)};
        }
    }

    @Override
    public void updateModel(ValueChange valueChange) {
        if (selectedElementBinding != null) {

            String newTextValue = valueChange.getMainValue();
            //TODO: remove this part
            if (newTextValue != null) {
                newTextValue = newTextValue.substring(1, newTextValue.length() - 1);
                this.selectedRowsNumbers = Arrays.stream(newTextValue.split(","))
                        .map(String::trim).mapToInt(Integer::parseInt).toArray();
                Object newSelectedElement;
                if (multiselect) {
                    newSelectedElement = getSelectedElementsBasedOnRowsNumbers(getBindedObjectsList(), this.selectedRowsNumbers);
                } else {
                    selectedRowsNumbers[0] = Integer.parseInt(newTextValue); // it is possible to be changed in future. We avoid sending changes that do nothing to the client, client won't lose incorrectly given value
                    if (selectedRowsNumbers[0] > -1 && getBindedObjectsList().size() > selectedRowsNumbers[0]) {
                        newSelectedElement = CollectionsUtils.get(getBindedObjectsList(), selectedRowsNumbers[0]);

                    } else {
                        newSelectedElement = null;
                    }
                }
                this.updateBindingForValue(newSelectedElement, selectedElementBinding, selectedElementBinding.getBindingExpression(), Optional.empty());
            }
        }
    }

    protected List<Object> getSelectedElementsBasedOnRowsNumbers(Collection<Object> bindedObjectsList, int[] selectedRowsNumbers) {
        List<Object> elements = new LinkedList<>();
        Arrays.stream(selectedRowsNumbers).filter(i -> i > -1 && bindedObjectsList.size() > i).forEach(i -> elements.add(CollectionsUtils.get(bindedObjectsList, i)));
        return elements;
    }

    protected Collection<Object> getBindedObjectsList() {
        if (this.getCollection() == null) {
            throw new FhBindingException("Table '" + this.getId() + "' has not binding for 'collection'!");
        }
        Form form = getForm();
        Object list = this.getCollection().getBindingResult().getValue();

        if (list != null) {
            if (list instanceof Collection) {
                return (Collection<Object>) list;
            } else {
                throw new FhBindingException("Binded for table '" + getId() + "' class object '" + list.getClass().getSimpleName() + "' is not a Collection!");
            }
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<ActionBinding> getEventHandler(InMessageEventData eventData) {
        if (eventData.getEventType().equals(ON_ROW_CLICK)) {
            return Optional.ofNullable(onRowClick);
        } else if (eventData.getEventType().equals(ON_ROW_DOUBLE_CLICK)){
            return Optional.ofNullable(onRowDoubleClick);
        } else {
            return super.getEventHandler(eventData);
        }
    }

    @Override
    // probably never used method - in future it should be removed
    public IEventSource getEventSource(String elementId) {
        int startingPointOfElement = elementId.indexOf("[");
        int lastPointOfElement = elementId.indexOf("]", startingPointOfElement);
        String indexOfElementAsString = elementId.substring(startingPointOfElement + 1, lastPointOfElement);
        int indexOfElement = Integer.parseInt(indexOfElementAsString);

        return getTableRows().get(indexOfElement).getEventSource(elementId);
    }

    @Override
    @JsonIgnore
    public List<Component> getSubcomponents() {
        return super.getSubcomponents();
    }

    public List<Iterator> getAllIterators() {
        if (allIterators == null || getForm().isDesignMode()) { // in design mode do not cache iterators
            allIterators = new ArrayList<>();
            if (getIterator() != null) {
                allIterators.add(new Iterator(getForm(), getIterator(), getCollection()));
            }
            allIterators.addAll(iterators);
        }
        return allIterators;
    }

    @Override
    public boolean isComponentFactorySupported() {
        return false;
    }

    @Override
    public void doActionForEverySubcomponentInlcudingRepeated(Consumer<Component> action) {
        super.doActionForEverySubcomponentInlcudingRepeated(action);
        for (ColumnOptimized column : getColumns()) {
            action.accept(column);
            column.doActionForEverySubcomponentInlcudingRepeated((Consumer) action);
            column.doActionForEveryRepeatedSubcomponent((Consumer) action);
        }
    }

    @Override
    public IGroupingComponent getGroupingComponent(Component formElement) {
        if (getColumns().contains(formElement)) {
            // this table direct column
            return this;
        } else {
            // this table nested column
            for (ColumnOptimized column : getColumns()) {
                IGroupingComponent<?> groupingColumn = column.getGroupingComponent(formElement);
                if (groupingColumn != null) {
                    return groupingColumn;
                }
            }
            // not a column of this table
            return super.getGroupingComponent(formElement);
        }
    }

    @Override
    public void removeSubcomponent(Component removedFormElement) {
        if (getColumns().contains(removedFormElement)) {
            getColumns().remove((ColumnOptimized) removedFormElement);
        } else {
            super.removeSubcomponent(removedFormElement);
        }
    }

    @Override
    public void onDesignerAddDefaultSubcomponent(SpacerService spacerService) {
        ColumnOptimized column = createExampleColumn(getColumns().size() + 1);
        // copy width from the last column
        if (!getColumns().isEmpty()) {
            column.setWidth(getColumns().get(getColumns().size() - 1).getWidth());
        }
        getColumns().add(column);
    }

    @Override
    public void onDesignerBeforeAdding(IGroupingComponent<?> parent, SpacerService spacerService) {
        this.setMinRows(1);
        getColumns().add(createExampleColumn(1));
        getColumns().add(createExampleColumn(2));
        getColumns().add(createExampleColumn(3));
    }

    public void setOnRowClick(ActionBinding onRowClick) {
        this.onRowClick = onRowClick;
    }

    public IActionCallbackContext setOnRowClick(IActionCallback onRowClick) {
        return CallbackActionBinding.createAndSet(onRowClick, this::setOnRowClick);
    }

    public void setOnRowDoubleClick(ActionBinding onRowDoubleClick) {
        this.onRowDoubleClick = onRowDoubleClick;
    }

    public IActionCallbackContext setOnRowDoubleClick(IActionCallback onRowDoubleClick) {
        return CallbackActionBinding.createAndSet(onRowDoubleClick, this::setOnRowDoubleClick);
    }

    @Override
    public void move(Component columnComponent, int vector) {
        maybeMoveColumn(getColumns(), columnComponent, vector);
    }

    public boolean isSelectionCheckboxes() {
        return multiselect && selectionCheckboxes;
    }

    private void maybeMoveColumn(List<ColumnOptimized> subcolumns, Component columnComponent, int vector) {
        IEditableGroupingComponent.move(subcolumns, (ColumnOptimized) columnComponent, vector);
        for (ColumnOptimized column : subcolumns) {
            maybeMoveColumn(column.getSubcolumns(), columnComponent, vector);
        }
    }

    protected boolean processLabelBinding(ElementChanges elementChanges) {
        BindingResult labelBidingResult = labelModelBinding != null ? labelModelBinding.getBindingResult() : null;
        String newLabelValue = labelBidingResult == null ? null : this.convertBindingValueToString(labelBidingResult);
        if (!areValuesTheSame(newLabelValue, label)) {
            this.label = newLabelValue;
            elementChanges.addChange(LABEL_ATTR, this.label);
            return true;
        }
        return false;
    }

    protected ColumnOptimized createExampleColumn(int nameSuffix) {
        ColumnOptimized column = new ColumnOptimized(getForm());
        column.setLabelModelBinding(new StaticBinding<>("ColumnOptimized " + nameSuffix));
        column.setTable(this);
        column.setGroupingParentComponent(this);
        column.init();
        return column;
    }
}
