package com.scchan.warpplus;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Called on the server thread. Persist before publishing a mutation to memory. */
public final class WarpRegistry {
    private final RegistryStore store;
    private final Clock clock;
    private Map<DomainName, WarpRecord> records;

    public WarpRegistry(RegistryStore store, Clock clock) throws IOException {
        this.store = store;
        this.clock = clock;
        var loaded = new TreeMap<DomainName, WarpRecord>();
        for (WarpRecord record : store.load()) {
            if (loaded.put(record.domain(), record) != null) {
                throw new IOException("Duplicate domain: " + record.domain());
            }
        }
        for (DomainName name : loaded.keySet()) {
            if (name.parent().isPresent() && !loaded.containsKey(name.parent().get())) {
                throw new IOException("Missing parent of " + name);
            }
        }
        records = loaded;
    }

    public WarpRecord require(DomainName domain) {
        WarpRecord record = records.get(domain);
        if (record == null) throw new IllegalArgumentException("Domain is not registered: " + domain);
        return record;
    }

    public Optional<WarpRecord> find(DomainName domain) { return Optional.ofNullable(records.get(domain)); }

    public Optional<WarpRecord> find(String name) {
        try { return find(new DomainName(name)); }
        catch (IllegalArgumentException ex) { return Optional.empty(); }
    }

    public List<WarpRecord> children(DomainName parent) {
        return records.values().stream()
                .filter(record -> Objects.equals(record.domain().parent().orElse(null), parent)).toList();
    }

    public List<DomainName> names() { return List.copyOf(records.keySet()); }

    public boolean canManage(Actor actor, DomainName domain) {
        if (actor.admin()) return true;
        if (!actor.canRegister() || actor.playerId() == null) return false;
        DomainName current = domain;
        while (current != null) {
            WarpRecord record = records.get(current);
            if (record != null && actor.playerId().equals(record.owner())) return true;
            current = current.parent().orElse(null);
        }
        return false;
    }

    public void register(Actor actor, DomainName domain, WarpLocation location) throws IOException {
        if (records.containsKey(domain)) throw new IllegalArgumentException("Domain is already registered: " + domain);
        checkRegistration(actor, domain);
        var next = new TreeMap<>(records);
        var now = clock.instant();
        next.put(domain, new WarpRecord(domain, actor.playerId(), now, now, Map.of(), location));
        commit(next);
    }

    /** Creates a linked record or replaces an owned record's destination in one durable mutation. */
    public void link(Actor actor, DomainName domain, String essentialsWarp) throws IOException {
        String target = WarpRecord.validateEssentialsWarp(Objects.requireNonNull(essentialsWarp));
        WarpRecord current = records.get(domain);
        var now = clock.instant();
        if (current == null) {
            checkRegistration(actor, domain);
            current = new WarpRecord(domain, actor.playerId(), now, now, Map.of(), null);
        } else {
            checkManage(actor, domain);
        }
        var next = new TreeMap<>(records);
        next.put(domain, current.withEssentialsWarp(target, now));
        commit(next);
    }

    private void checkRegistration(Actor actor, DomainName domain) {
        if (!actor.admin() && !actor.canRegister()) throw new IllegalArgumentException("You cannot register domains.");
        DomainName parent = domain.parent().orElse(null);
        if (parent == null) {
            if (!actor.admin() && !actor.canRegisterRoot()) {
                throw new IllegalArgumentException("Only registrars can create top-level domains. Ask an admin to delegate one to you.");
            }
        } else {
            require(parent);
            checkManage(actor, parent);
        }
    }

    public void setField(Actor actor, DomainName domain, MetadataField field, String value) throws IOException {
        update(actor, domain, record -> record.withField(field, value, clock.instant()));
    }

    public void setLocation(Actor actor, DomainName domain, WarpLocation location) throws IOException {
        update(actor, domain, record -> record.withLocation(location, clock.instant()));
    }

    public void transfer(Actor actor, DomainName domain, UUID owner) throws IOException {
        update(actor, domain, record -> record.withOwner(owner, clock.instant()));
    }

    public void delete(Actor actor, DomainName domain) throws IOException {
        require(domain);
        checkManage(actor, domain);
        if (!children(domain).isEmpty()) throw new IllegalArgumentException("Delete the children first; domains with children cannot be deleted.");
        var next = new TreeMap<>(records);
        next.remove(domain);
        commit(next);
    }

    private void update(Actor actor, DomainName domain, UnaryOperator<WarpRecord> change) throws IOException {
        WarpRecord record = require(domain);
        checkManage(actor, domain);
        var next = new TreeMap<>(records);
        next.put(domain, change.apply(record));
        commit(next);
    }

    private void checkManage(Actor actor, DomainName domain) {
        if (!canManage(actor, domain)) throw new IllegalArgumentException("Only this domain's owner, an ancestor's owner, or an admin can manage it.");
    }

    private void commit(Map<DomainName, WarpRecord> next) throws IOException {
        store.save(next.values());
        records = next;
    }
}
