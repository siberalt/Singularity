package investtech.broker.contract.service.user;
import java.time.Instant;

public class Account {
    protected String id;
    protected AccountType type;
    protected String name;
    protected AccountStatus status;
    protected Instant openedDate;
    protected Instant closedDate;
    protected AccessLevel accessLevel;

    public String getId() {
        return id;
    }

    public Account setId(String id) {
        this.id = id;
        return this;
    }

    public AccountType getType() {
        return type;
    }

    public Account setType(AccountType type) {
        this.type = type;
        return this;
    }

    public String getName() {
        return name;
    }

    public Account setName(String name) {
        this.name = name;
        return this;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Account setStatus(AccountStatus status) {
        this.status = status;
        return this;
    }

    public Instant getOpenedDate() {
        return openedDate;
    }

    public Account setOpenedDate(Instant openedDate) {
        this.openedDate = openedDate;
        return this;
    }

    public Instant getClosedDate() {
        return closedDate;
    }

    public Account setClosedDate(Instant closedDate) {
        this.closedDate = closedDate;
        return this;
    }

    public AccessLevel getAccessLevel() {
        return accessLevel;
    }

    public Account setAccessLevel(AccessLevel accessLevel) {
        this.accessLevel = accessLevel;
        return this;
    }
}
