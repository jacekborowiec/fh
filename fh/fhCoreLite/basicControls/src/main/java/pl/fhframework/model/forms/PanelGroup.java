package pl.fhframework.model.forms;

import com.fasterxml.jackson.annotation.JsonIgnore;

import pl.fhframework.core.forms.IHasBoundableLabel;
import pl.fhframework.BindingResult;
import pl.fhframework.annotations.*;
import pl.fhframework.annotations.Control;
import pl.fhframework.binding.*;
import pl.fhframework.model.dto.ElementChanges;
import pl.fhframework.model.dto.ValueChange;
import pl.fhframework.model.forms.designer.BindingExpressionDesignerPreviewProvider;
import pl.fhframework.model.dto.InMessageEventData;
import pl.fhframework.events.IEventSource;

import lombok.Getter;
import lombok.Setter;
import pl.fhframework.model.forms.designer.IDesignerEventListener;

import java.util.Optional;

import static pl.fhframework.annotations.DesignerXMLProperty.PropertyFunctionalArea.BEHAVIOR;
import static pl.fhframework.annotations.DesignerXMLProperty.PropertyFunctionalArea.CONTENT;
import static pl.fhframework.annotations.DesignerXMLProperty.PropertyFunctionalArea.LOOK_AND_STYLE;

@Control(parents = {Accordion.class, PanelGroup.class, Group.class, SplitContainer.class, Repeater.class, Column.class, Tab.class, Row.class, Form.class}, invalidParents = {Table.class}, canBeDesigned = true)
@DocumentedComponent(value = "PanelGroup component is responsible for the grouping of sub-elements with optional header and collapsing", icon = "fa fa-object-group")
public class PanelGroup extends GroupingComponent<Component> implements Boundable, IChangeableByClient, IEventSource, IHasBoundableLabel, IDesignerEventListener {

    private static final String COLLAPSED_ATTR = "collapsed";
    public static final String ON_TOGGLE = "onToggle";
    public static final String LABEL_ATTR = "label";
    public static final String BORDER_VISIBLE_ATTR = "borderVisible";

    @Getter
    private String label;

    @JsonIgnore
    @Getter
    @Setter
    @XMLProperty(value = LABEL_ATTR)
    @DesignerXMLProperty(commonUse = true, previewValueProvider = BindingExpressionDesignerPreviewProvider.class, priority = 100, functionalArea = CONTENT)
    @DocumentedComponentAttribute(boundable = true, value = "Represents label for created component. Supports FHML - FH Markup Language.")
    private ModelBinding<String> labelModelBinding;

    @Getter
    @Setter
    @XMLProperty
    @DocumentedComponentAttribute(defaultValue = "false", value = "Defines if group can be collapsed or expanded. False by default.")
    private boolean collapsible = false;

    @Getter
    @Setter
    @XMLProperty(value=BORDER_VISIBLE_ATTR)
    @DesignerXMLProperty(functionalArea = LOOK_AND_STYLE, priority = 76)
    @DocumentedComponentAttribute(defaultValue = "false", value = "Defines if group should have border. False by default.")
    private boolean borderVisible = false;

    @Getter
    @XMLProperty
    @DesignerXMLProperty(functionalArea = BEHAVIOR)
    @DocumentedComponentAttribute("Name of event which will be invoked after cliking collapse group button. Over it it also defines if group can be collapsed or expanded. " +
            "Empty by default (not collapsible).")
    private ActionBinding onToggle;

    @Getter
    private boolean collapsed = false;

    @JsonIgnore
    @Getter
    @Setter
    @XMLProperty(value = COLLAPSED_ATTR, defaultValue = "false")
    @DocumentedComponentAttribute(defaultValue = "false", boundable = true, value = "Contains a state for collapsible group, if the group is currently collapsed or expanded." +
            " Depends on attribute: collapsible, if it's not set then binding will not be resolved. False by default.")
    private ModelBinding modelBindingForState = new StaticBinding<>(false);

    public PanelGroup(Form form) {
        super(form);
    }

    @Override
    public ElementChanges updateView() {
        ElementChanges elementChanges = super.updateView();
        resolveBindingForLabel(elementChanges);
        if (isCollapsible()) {
            resolveBindingForCollapsed(elementChanges);
        }
        return elementChanges;
    }

    private void resolveBindingForCollapsed(ElementChanges elementChanges) {
        if (this.modelBindingForState != null) {
            BindingResult bindingResultForCollapsed = this.modelBindingForState.getBindingResult();
            if (bindingResultForCollapsed != null) {
                boolean newValue = Boolean.valueOf(this.convertBindingValueToString(bindingResultForCollapsed));
                if (this.collapsed != newValue) {
                    refreshView();
                    this.collapsed = newValue;
                    elementChanges.addChange(COLLAPSED_ATTR, this.collapsed);
                }
            }
        }
    }

    private void resolveBindingForLabel(ElementChanges elementChanges) {
        if(labelModelBinding != null) {
            label = labelModelBinding.resolveValueAndAddChanges(this, elementChanges, label, LABEL_ATTR);
        }
    }

    @Override
    public void updateModel(ValueChange valueChange) {
        if (this.modelBindingForState != null && valueChange.hasMainValueChanged()) {
            updateBinding(valueChange, this.modelBindingForState, this.modelBindingForState.getBindingExpression());
        }
    }

    @Override
    public Optional<ActionBinding> getEventHandler(InMessageEventData eventData) {
        if (ON_TOGGLE.equals(eventData.getEventType())) {
            return Optional.ofNullable(onToggle);
        } else {
            return super.getEventHandler(eventData);
        }
    }

    public void setOnToggle(ActionBinding onToggle) {
        this.onToggle = onToggle;
    }

    public IActionCallbackContext setOnToggle(IActionCallback onToggle) {
        return CallbackActionBinding.createAndSet(onToggle, this::setOnToggle);
    }

    @Override
    public void onDesignerAddDefaultSubcomponent(SpacerService spacerService) {
        addSubcomponent(createNewRow());
    }

    @Override
    public void onDesignerBeforeAdding(IGroupingComponent<?> parent, SpacerService spacerService) {
        addSubcomponent(createNewRow());
    }

    private Row createNewRow() {
        Row row = new Row(getForm());
        row.setGroupingParentComponent(this);
        row.init();
        return row;
    }
}
