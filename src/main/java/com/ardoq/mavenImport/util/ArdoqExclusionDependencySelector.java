package com.ardoq.mavenImport.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;

public class ArdoqExclusionDependencySelector implements DependencySelector
{

    // sorted and dupe-free array, faster to iterate than LinkedHashSet
    private final Exclusion[] exclusions;

    private int hashCode;

    // Added by Ardoq to keep track of excluded artifacts
    private Map<Artifact,ArdoqExclusionDependencySelector> dependencySelectors;
    private List<Artifact> excluded = new LinkedList<Artifact>();



    /**
     * Creates a new selector without any exclusions.
     * @param dependencySelectors
     */
    public ArdoqExclusionDependencySelector(Map<Artifact,ArdoqExclusionDependencySelector> dependencySelectors)
    {
        this.dependencySelectors = dependencySelectors;
        this.exclusions = new Exclusion[0];
    }

    /**
     * Creates a new selector with the specified exclusions.
     * @param dependencySelectors
     * @param exclusions
     */
    public ArdoqExclusionDependencySelector(Map<Artifact,ArdoqExclusionDependencySelector> dependencySelectors, Collection<Exclusion> exclusions )
    {
        this.dependencySelectors = dependencySelectors;
        if ( exclusions != null && !exclusions.isEmpty() )
        {
            TreeSet<Exclusion> sorted = new TreeSet<Exclusion>( ExclusionComparator.INSTANCE );
            sorted.addAll( exclusions );
            this.exclusions = sorted.toArray( new Exclusion[sorted.size()] );
        }
        else
        {
            this.exclusions = new Exclusion[0];
        }
    }

    /**
     *
     * @param dependencySelectors
     * @param exclusions
     */
    private ArdoqExclusionDependencySelector(Map<Artifact,ArdoqExclusionDependencySelector> dependencySelectors, Exclusion[] exclusions )
    {
        this.dependencySelectors = dependencySelectors;
        this.exclusions = exclusions;
    }

    public Map<Artifact, ArdoqExclusionDependencySelector> getDependencySelectors() {
        return dependencySelectors;
    }

    public List<Artifact> getExcluded() {
        return excluded;
    }

    public boolean selectDependency( Dependency dependency )
    {
        Artifact artifact = dependency.getArtifact();
        for ( Exclusion exclusion : exclusions )
        {
            if ( matches( exclusion, artifact ) )
            {
                excluded.add(artifact);
                return false;
            }
        }
        return true;
    }

    private boolean matches( Exclusion exclusion, Artifact artifact )
    {
        if ( !matches( exclusion.getArtifactId(), artifact.getArtifactId() ) )
        {
            return false;
        }
        if ( !matches( exclusion.getGroupId(), artifact.getGroupId() ) )
        {
            return false;
        }
        if ( !matches( exclusion.getExtension(), artifact.getExtension() ) )
        {
            return false;
        }
        if ( !matches( exclusion.getClassifier(), artifact.getClassifier() ) )
        {
            return false;
        }
        return true;
    }

    private boolean matches( String pattern, String value )
    {
        return "*".equals( pattern ) || pattern.equals( value );
    }

    public DependencySelector deriveChildSelector( DependencyCollectionContext context )
    {
        Dependency dependency = context.getDependency();
        Collection<Exclusion> exclusions = ( dependency != null ) ? dependency.getExclusions() : null;
        if ( exclusions == null)
        {
            exclusions = new HashSet<Exclusion>();
        }

        Exclusion[] merged = this.exclusions;
        int count = merged.length;
        for ( Exclusion exclusion : exclusions )
        {
            int index = Arrays.binarySearch( merged, exclusion, ExclusionComparator.INSTANCE );
            if ( index < 0 )
            {
                index = -( index + 1 );
                if ( count >= merged.length )
                {
                    Exclusion[] tmp = new Exclusion[merged.length + exclusions.size()];
                    System.arraycopy( merged, 0, tmp, 0, index );
                    tmp[index] = exclusion;
                    System.arraycopy( merged, index, tmp, index + 1, count - index );
                    merged = tmp;
                }
                else
                {
                    System.arraycopy( merged, index, merged, index + 1, count - index );
                    merged[index] = exclusion;
                }
                count++;
            }
        }
//        if ( merged == this.exclusions )
//        {
//            return this;
//        }
        if ( merged.length != count )
        {
            Exclusion[] tmp = new Exclusion[count];
            System.arraycopy( merged, 0, tmp, 0, count );
            merged = tmp;
        }

        ArdoqExclusionDependencySelector child = new ArdoqExclusionDependencySelector( dependencySelectors, merged );
        dependencySelectors.put(dependency.getArtifact(), child);
        return child;
    }


    @Override
    public int hashCode()
    {
        if ( hashCode == 0 )
        {
            int hash = getClass().hashCode();
            hash = hash * 31 + Arrays.hashCode( exclusions );
            hashCode = hash;
        }
        return hashCode;
    }

    private static class ExclusionComparator
        implements Comparator<Exclusion>
    {

        static final ExclusionComparator INSTANCE = new ExclusionComparator();

        public int compare( Exclusion e1, Exclusion e2 )
        {
            if ( e1 == null )
            {
                return ( e2 == null ) ? 0 : 1;
            }
            else if ( e2 == null )
            {
                return -1;
            }
            int rel = e1.getArtifactId().compareTo( e2.getArtifactId() );
            if ( rel == 0 )
            {
                rel = e1.getGroupId().compareTo( e2.getGroupId() );
                if ( rel == 0 )
                {
                    rel = e1.getExtension().compareTo( e2.getExtension() );
                    if ( rel == 0 )
                    {
                        rel = e1.getClassifier().compareTo( e2.getClassifier() );
                    }
                }
            }
            return rel;
        }

    }

}
