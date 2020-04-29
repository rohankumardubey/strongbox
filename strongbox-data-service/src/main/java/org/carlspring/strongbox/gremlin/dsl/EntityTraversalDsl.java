package org.carlspring.strongbox.gremlin.dsl;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.GremlinDsl;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty.Cardinality;
import org.carlspring.strongbox.data.domain.DomainObject;
import org.carlspring.strongbox.gremlin.adapters.UnfoldEntityTraversal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

/**
 * @author sbespalov
 *
 * @param <S>
 * @param <E>
 */
@GremlinDsl(traversalSource = "org.carlspring.strongbox.gremlin.dsl.EntityTraversalSourceDsl")
public interface EntityTraversalDsl<S, E> extends GraphTraversal.Admin<S, E>
{

    Logger logger = LoggerFactory.getLogger(EntityTraversalDsl.class);

    Object NULL = new Object()
    {

        @Override
        public String toString()
        {
            return "__null";
        }

    };

    @SuppressWarnings("unchecked")
    default GraphTraversal<S, Vertex> findById(Object uuid,
                                               String... labels)
    {
        GraphTraversal<S, Vertex> result = (GraphTraversal<S, Vertex>) has(labels[0], "uuid", uuid);
        for (String label : Arrays.copyOfRange(labels, 1, labels.length))
        {
            result = result.fold().choose(Collection::isEmpty, __.V().has(label, "uuid", uuid), __.unfold());
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    default Traversal<S, Object> enrichPropertyValue(String propertyName)
    {
        return coalesce(__.properties(propertyName).value(), __.<Object>constant(NULL));
    }

    @SuppressWarnings("unchecked")
    default Traversal<S, Object> enrichPropertyValues(String propertyName)
    {
        return coalesce(__.propertyMap(propertyName).map(t -> t.get().get(propertyName)), __.<Object>constant(NULL));
    }

    default <S2> Traversal<S, Object> mapToObject(Traversal<S2, Object> enrichObjectTraversal)
    {
        return fold().choose(t -> t.isEmpty(),
                             __.<Object>constant(NULL),
                             __.<Edge>unfold().map(enrichObjectTraversal));
    }

    default GraphTraversal<S, Vertex> V(DomainObject entity)
    {
        Long nativeId = entity.getNativeId();
        if (nativeId != null)
        {
            return V(nativeId);
        }

        return V();
    }
    
    default <S2> Traversal<S, Vertex> saveV(Object uuid,
                                            UnfoldEntityTraversal<S2, Vertex> unfoldTraversal)
    {
        uuid = Optional.ofNullable(uuid)
                       .orElse(NULL);
        DomainObject entity = unfoldTraversal.getEntity();
        String label = unfoldTraversal.getEntityLabel();
        
        return hasLabel(label).has("uuid", uuid)
                              .fold()
                              .choose(Collection::isEmpty,
                                      __.addV(label)
                                        .property("uuid",
                                                  Optional.of(uuid)
                                                          .filter(x -> !NULL.equals(x))
                                                          .orElse(UUID.randomUUID().toString()))
                                        .property("created", System.currentTimeMillis())
                                        .info("Created"),
                                      __.unfold()
                                        .debug("Fetched"))
                              .map(unfoldTraversal)
                              .sideEffect(t -> {
                                  entity.applyUnfold(t);
                              });
    }

    @SuppressWarnings("unchecked")
    default <E2> Traversal<S, E2> info(String action)
    {
        return (Traversal<S, E2>) sideEffect(t -> logger.info(String.format("%s [%s]-[%s]-[%s]",
                                                                            action,
                                                                            ((Element) t.get()).label(),
                                                                            ((Element) t.get()).id(),
                                                                            ((Element) t.get()).property("uuid")
                                                                                               .orElse("null"))));
    }

    @SuppressWarnings("unchecked")
    default <E2> Traversal<S, E2> debug(String action)
    {
        return (Traversal<S, E2>) sideEffect(t -> logger.debug(String.format("%s [%s]-[%s]-[%s]",
                                                                             action,
                                                                             ((Element) t.get()).label(),
                                                                             ((Element) t.get()).id(),
                                                                             ((Element) t.get()).property("uuid")
                                                                                                .orElse("null"))));
    }

    default <E2> GraphTraversal<S, E2> property(final String key,
                                                final Set<String> values)
    {

        if (CollectionUtils.isEmpty(values))
        {
            return (GraphTraversal<S, E2>) identity();
        }

        GraphTraversal<S, E2> t = (GraphTraversal<S, E2>) property(Cardinality.set, key, "");
        for (String value : values)
        {
            t.property(Cardinality.set, key, value);
        }

        return t;
    }
}