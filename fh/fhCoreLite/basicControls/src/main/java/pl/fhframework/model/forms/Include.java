package pl.fhframework.model.forms;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ReflectionUtils;
import pl.fhframework.binding.AdHocModelBinding;
import pl.fhframework.binding.StaticBinding;
import pl.fhframework.core.FhException;
import pl.fhframework.core.FhFormException;
import pl.fhframework.core.dynamic.DynamicClassName;
import pl.fhframework.core.events.OnEvent;
import pl.fhframework.core.logging.FhLogger;
import pl.fhframework.annotations.*;
import pl.fhframework.annotations.composite.Composite;
import pl.fhframework.binding.ModelBinding;
import pl.fhframework.core.util.StringUtils;
import pl.fhframework.forms.IFormsUtils;
import pl.fhframework.helper.AutowireHelper;
import pl.fhframework.model.dto.ElementChanges;
import pl.fhframework.tools.loading.FormReader;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Created by krzysztof.kobylarek on 2016-12-20.
 */

@Control(canBeDesigned = true)
@DesignerControl
@DocumentedComponent(value = "Component used to include xml templates into main form view", icon = "fa fa-cubes")
@JsonIgnoreType
@JsonSerialize(using = Include.Serializer.class)
public class Include extends FormElement implements IGroupingComponent<Component>, Includeable {

    private static final String ATTR_REF = "ref";
    private static final String ATTR_MODEL = "model";
    private static final String ATTR_MODEL_VALUE = "modelValue";
    private static final String ATTR_VARIANT = "variant";

    @Autowired
    private IFormsUtils formsManager;

    private static AtomicLong incId = new AtomicLong(0);

    @JsonIgnore
    @Getter
    @Setter
    @XMLProperty(value = ATTR_REF, required = true)
    @DocumentedComponentAttribute(value = "Reference to composite type")
    private ModelBinding<String> refBinding;

    @Getter
    protected String ref = "";

    @JsonIgnore
    @Getter
    @Setter
    @XMLProperty(value = ATTR_MODEL, aliases = "model", required = false)
    @DocumentedComponentAttribute(value = "Reference to model instance (property path) as a String. It is recommended to use " + ATTR_MODEL_VALUE + " attribute instead.")
    private ModelBinding<String> modelRefBinding;

    @Getter
    protected String modelRef = "";

    @JsonIgnore
    @Getter
    @Setter
    @XMLProperty(value = ATTR_MODEL_VALUE, required = false)
    @DocumentedComponentAttribute(value = "Model instance")
    private ModelBinding<?> modelValueBinding;

    @JsonIgnore
    @Getter
    @Setter
    @XMLProperty(value = ATTR_VARIANT, aliases = "variant", required = false)
    @DocumentedComponentAttribute(value = "Reference to variant as a String.")
    private ModelBinding<String> variantBinding;

    @Getter
    protected String variant;

    protected Form includedComposite = null;

    @Getter
    @Setter
    @XMLMetadataSubelements
    private List<OnEvent> registeredEvents = new LinkedList<>();

    public Include(Form form) {
        super(form);
    }

    @Override
    public void init() {
        super.init();

        if (refBinding == null) {
            throw new FhException("No composite reference");
        }

        // autowire forms manager
        AutowireHelper.autowire(this, formsManager);

        this.resolveBinding();

        if (ref == null) {
            return;
        }

        readComposite();

        includedComposite.setId(getForm().getId());
        includedComposite.setGroupingParentComponent(this);
        includedComposite.setUseCase(getForm().getAbstractUseCase());

        includedComposite.setModelProvider(resolveModelProvider());

        if (includedComposite instanceof CompositeForm) {
            ((CompositeForm)includedComposite).addRegisteredEvents(registeredEvents);
        }

        if (!includedComposite.isInitDone()) {
            includedComposite.init();
        }

        if (!getForm().getIncluded().contains(this)) {
            getForm().getIncluded().add(this);
        }

        doActionForEverySubcomponent(component -> {
            if (!component.isInitDone()) {
                component.init();
            }
        });

        includedComposite.setVariant(variant != null ? variant : getForm().getVariant());
        includedComposite.setAvailabilityRules(includedComposite.getVariant());

        doActionForEverySubcomponent(
                (Component c) -> {
                    if (c.getId() == null) {
                        c.generateId();
                    }
                    if (!c.isGeneratedId()) {
                        c.setRawId(c.getId());
                        c.setId(c.getId() + "_" + incId.getAndIncrement());
                    }
                });

        this.doActionForEverySubcomponent(c -> {
            if (c instanceof FormElement) {
                getForm().addToElementIdToFormElement((FormElement) c);
            }
        });
    }

    protected Supplier resolveModelProvider() {
        if (getForm().getModel() != null && getForm().getViewMode() == Form.ViewMode.NORMAL) {
            if (modelValueBinding != null) {
                return () -> modelValueBinding.getBindingResult().getValue();
            } else if (modelRef != null) {
                return () -> {
                    final String[] splittedModel = modelRef.split("\\.");
                    Object compositeModel = getForm().getModel();
                    Class<?> modelClass = getForm().getModel().getClass();
                    for (int i = 0; i < splittedModel.length; i++) {
                        if (i == 0 && splittedModel[i].equals("THIS")) {
                            modelClass = compositeModel.getClass();
                        } else {
                            Method modelGetter = ReflectionUtils.findMethod(modelClass, toGetter(splittedModel[i]));
                            if (modelGetter != null && Modifier.isPublic(modelGetter.getModifiers())) {
                                compositeModel = ReflectionUtils.invokeMethod(modelGetter, compositeModel);
                                modelClass = compositeModel.getClass();
                            } else {
                                throw new FhFormException(
                                        String.format("Composite model %s in %s not found or not accessible",
                                                modelRef, getForm().getModel().getClass().getSimpleName())
                                );
                            }
                        }
                    }
                    return compositeModel;
                };
            }
        }
        return () -> null;
    }

    protected void readComposite() {
        FormReader formReader = FormReader.getInstance();
        Class<? extends Form> compositeComponentClass = formReader.getCompositesClasses().get(ref);
        if (compositeComponentClass != null) {
            includedComposite = formsManager.createFormInstance(compositeComponentClass, getForm().getComponentBindingCreator(), getForm().getBindingMethodsCreator());
        }
        else {
            Class<? extends Form> formClass = formsManager.getFormById(ref);
            if (formClass != null) {
                includedComposite = formsManager.createFormInstance(formClass, getForm().getComponentBindingCreator(), getForm().getBindingMethodsCreator());
            }
            else {
                FhLogger.error(ref + " is not a valid included form.");
                return;
            }
        }
    }

    private void resolveBinding() {
        if(this.modelRefBinding != null) {
            modelRef = (String) this.modelRefBinding.resolveValue(modelRef);
        }
        if(this.refBinding != null) {
            ref = (String) this.refBinding.resolveValue(ref);
        }
        if(this.variantBinding != null) {
            variant = (String) this.variantBinding.resolveValue(variant);
        }
    }

    @Override
    public void doActionForEverySubcomponent(Consumer<Component> action) {
        getSubcomponents().stream().forEachOrdered((Component c) -> {
            action.accept(c);
            if (c instanceof IGroupingComponent) {
                ((IGroupingComponent) c).doActionForEverySubcomponent(action);
            }
        });
    }

    @Override
    public void activateBindings() {
        if (includedComposite != null) {
            includedComposite.activateBindings();
        }
    }

    @Override
    public void deactivateBindings() {
        if (includedComposite != null) {
            includedComposite.deactivateBindings();
        }
    }

    @Override
    public void afterNestedComponentsProcess() {

        getForm().refreshElementIdToFormElement(); // for now
//

//        doActionForEverySubcomponent(formElement -> {
//            if(formElement instanceof FormElement) {
//                getForm().refreshElementView((FormElement) formElement);
//            }
//        });
//        }
    }

    @Override
    public void refreshView(Set<ElementChanges> changeSet) {
        super.refreshView(changeSet);
        if(includedComposite != null) {
            includedComposite.getSubcomponents().forEach((c) -> ((Component) c).refreshView(changeSet)); // to jakas glupota, kompilator wnioskuje brak typu ale powinien byc Component!
        }
    }

    @Override
    public void addSubcomponent(Component component) {
        if(includedComposite != null) {
            includedComposite.addSubcomponent(component);
        }
    }

    @Override
    public void removeSubcomponent(Component removedComponent) {
        if(includedComposite != null) {
            includedComposite.removeSubcomponent(removedComponent);
        }
    }

    @Override
    public IGroupingComponent getGroupingComponent(Component component) {
        if(includedComposite != null) {
            return includedComposite.getGroupingParentComponent();
        } else {
            return getGroupingParentComponent();
        }
    }

    @Override
    public List<Component> getSubcomponents() {
        if (includedComposite != null) {
            return includedComposite.getSubcomponents();
        } else {
            return new LinkedList<>();
        }
    }

    @Override
    public List<NonVisualFormElement> getNonVisualSubcomponents() {
        return Collections.emptyList();
    }

    @Override
    public List<Component> getIncludedComponents() {
        return getSubcomponents();
    }

    public void addRegisteredEvent(OnEvent onEvent){
        registeredEvents.add(onEvent);
    }

    static class Serializer extends JsonSerializer<Include> {


        @Override
        public void serialize(Include tag, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (tag != null && tag.getSubcomponents() != null) {
                Group group = new Group(tag.getForm());
                group.setId(tag.getId());
                group.getSubcomponents().addAll(tag.getSubcomponents());
                gen.writeObject(group);
            }
        }
    }

    private static String toGetter(String fieldName) {
        StringBuilder sb = new StringBuilder();
        return sb.append("get").append(fieldName.substring(0, 1).toUpperCase()).append(fieldName.substring(1, fieldName.length())).toString();
    }

    @Override
    public void preConfigureClear() {
        super.preConfigureClear();
        includedComposite = null;
        resetInitDone();
    }

    @Override
    public String getStaticRef() {
        if (!StringUtils.isNullOrEmpty(ref)) {
            return ref;
        }
        String staticRefForm = null;
        if (refBinding instanceof StaticBinding) {
            staticRefForm =  ((StaticBinding<String>) refBinding).getStaticValue();
        }
        else if (refBinding instanceof AdHocModelBinding && ((AdHocModelBinding<String>) refBinding).isStaticValue()) {
            staticRefForm = ((AdHocModelBinding<String>) refBinding).getStaticValueText();
        }
        if (!StringUtils.isNullOrEmpty(staticRefForm)) {
            Class<?> compositeForm = FormReader.getInstance().getCompositesClasses().get(((AdHocModelBinding) refBinding).getStaticValueText());
            if (compositeForm != null) {
                return DynamicClassName.forClassName(compositeForm.getName()).toFullClassName();
            }
            return staticRefForm;
        }
        return null;
    }

    @Override
    public void calculateAvailability() {
        super.calculateAvailability();
        if (includedComposite != null) {
            includedComposite.calculateAvailability();
        }
    }
}
