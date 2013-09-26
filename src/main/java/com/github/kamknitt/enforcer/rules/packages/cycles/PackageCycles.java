package com.github.kamknitt.enforcer.rules.packages.cycles;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Collections;

import com.github.kamknitt.enforcer.rules.packages.cycles.model.Cycle;
import edu.emory.mathcs.backport.java.util.Arrays;
import jdepend.framework.JDepend;
import jdepend.framework.JavaPackage;
import org.apache.maven.enforcer.rule.api.EnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class PackageCycles implements EnforcerRule {
    private Log log;
    private String exclusionPackagesFile = null;
    private boolean failBuild = true;

    @Override
    public void execute(EnforcerRuleHelper enforcerRuleHelper) throws EnforcerRuleException {
        log = enforcerRuleHelper.getLog();

        try {
            MavenProject mavenProject = (MavenProject)enforcerRuleHelper.evaluate("${project}");
            File targetDir = new File((String)enforcerRuleHelper.evaluate("${project.build.directory}"));
            File classesDir = new File(targetDir, "classes");

            if (classesDir.exists()) {
                JDepend jDepend = new JDepend();
                jDepend.addDirectory(classesDir.getAbsolutePath());
                Collection<JavaPackage> packages = jDepend.analyze();

                List<Cycle> normalizedCycles = new LinkedList<>();

                if (jDepend.containsCycles()) {
                    for (JavaPackage javaPackage : packages) {
                        List<JavaPackage> cycle = new LinkedList<>();
                        if (javaPackage.collectCycle(cycle) && !isReferenceToCycle(cycle)) {
                            for(List<String> c : extractCycles(cycle)) {
                                normalizedCycles.add(normalize(c));
                            }
                        }
                    }

                    if(parseCycles(removeDuplicates(normalizedCycles), skipPackage())) {
                        throw new EnforcerRuleException("Package cycles detected");
                    }
                }
            } else if (!classesDir.exists()) {
                log.warn("Skipping JDepend analisis. Target: " + classesDir.getAbsolutePath() + " does not exists");
            }
        } catch (ExpressionEvaluationException e) {
            throw new EnforcerRuleException("Cannot evaluate expression. " + e.getMessage());
        } catch (IOException e) {
            throw new EnforcerRuleException("IO exception. " + e.getMessage());
        }
    }

    @Override
    public boolean isCacheable() {
        return false;
    }

    @Override
    public boolean isResultValid(EnforcerRule enforcerRule) {
        return false;
    }

    @Override
    public String getCacheId() {
        return null;
    }

    private boolean isReferenceToCycle(List<JavaPackage> cycles) {
        JavaPackage first = cycles.get(0);
        for (int i = 1; i < cycles.size(); i++) {
            if(first.getName().equals(cycles.get(i).getName())) {
                return false;
            }
        }
        return true;
    }

    List<List<String>> extractCycles(List<JavaPackage> cycles) {
        List<List<String>> cyclesList = new LinkedList<>();

        List<String> cycle = new LinkedList<>();
        JavaPackage search = cycles.get(0);
        for (int i=1; i<cycles.size(); i++) {
            cycle.add(cycles.get(i).getName());
            if(cycles.get(i).equals(search)) {
                cyclesList.add(cycle);
                cycle = new LinkedList<>();
            }
        }

        return cyclesList;
    }

    Cycle normalize(List<String> cycle) {
        int min = cycle.indexOf(Collections.min(cycle));
        Cycle normalized = new Cycle(cycle.size());
        int i = min;
        do {
            normalized.add(cycle.get(i));
            i = (i+1)%cycle.size();
        } while(i != min);

        return normalized;
    }

    List<Cycle> removeDuplicates(List<Cycle> cycles) {
        Set setItems = new LinkedHashSet(cycles);
        return new LinkedList<Cycle>(setItems);
    }

    boolean parseCycles(List<Cycle> cycles, List<String> skip) {
        boolean error = false;
        for(Cycle c : cycles) {
            // no common elements, nothing to skip
            if(Collections.disjoint(c, skip)) {
                error = true;
                log.error("Package dependency cycles detected:");
                for(String s : c) {
                    log.error("    -> " + s);
                }
            } else {
                log.warn("Package dependency cycles detected (SKIPPING):");
                for(String s : c) {
                    log.warn("    -> " + s);
                }
            }
        }
        return failBuild && error;
    }

    private List<String> skipPackage() {
        List<String> skip = new LinkedList<>();

        if (exclusionPackagesFile == null) {
            log.info("Packages exclusion file not specified. Skipping.");
            return skip;
        } else {
            log.info("Trying to open packages exclusion file: " + exclusionPackagesFile);

            BufferedReader br = null;
            try {
                br = new BufferedReader(new FileReader(exclusionPackagesFile));
                String line;
                while((line = br.readLine()) != null) {
                    skip.addAll(Arrays.asList(line.split(",|;")));
                }

            } catch (FileNotFoundException e) {
                log.warn("Cannot open file! Skipping.");
                return skip;
            } catch (IOException e) {
                log.warn("Cannot read file! Skipping.");
                return skip;
            } finally {
                try {
                    if (br!= null) br.close();
                } catch (IOException e) {
                    log.warn("IO exception occurred during closing a file.");
                }
            }
            if (!skip.isEmpty()) {
                log.info("Packages to be skipped:");
                for (String s : skip) {
                    log.info(" : " + s);
                }
            }
            return skip;
        }
    }

    public void setExclusionPackagesFile(String exclusionPackagesFile) {
        this.exclusionPackagesFile = exclusionPackagesFile;
    }

    public void setFailBuild(boolean failBuild) {
        this.failBuild = failBuild;
    }
}
