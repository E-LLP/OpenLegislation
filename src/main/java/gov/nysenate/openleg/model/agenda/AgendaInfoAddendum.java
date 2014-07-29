package gov.nysenate.openleg.model.agenda;

import gov.nysenate.openleg.model.base.BaseLegislativeContent;
import gov.nysenate.openleg.model.entity.CommitteeId;
import org.joda.time.LocalDate;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class AgendaInfoAddendum extends BaseLegislativeContent implements Serializable
{
    private static final long serialVersionUID = 8661290465080663674L;

    /** Reference to the parent agenda. */
    private AgendaId agendaId;

    /** Each addendum has a character designator. */
    private String id;

    /** The week this agenda is for. */
    private Date weekOf;

    /** Committee information including bills up for consideration. */
    private Map<CommitteeId, AgendaInfoCommittee> committeeInfoMap;

    /** --- Constructors --- */

    public AgendaInfoAddendum() {
        super();
        this.committeeInfoMap = new HashMap<>();
    }

    public AgendaInfoAddendum(String id, Date weekOf, Date pubDate) {
        this();
        this.setId(id);
        this.setWeekOf(weekOf);
        this.setModifiedDate(pubDate);
        this.setPublishDate(pubDate);
        this.setYear(new LocalDate(pubDate).getYear());
        this.setSession(resolveSessionYear(this.getYear()));
    }

    /** --- Functional Getters/Setters --- */

    public void putCommittee(AgendaInfoCommittee infoCommittee) {
        this.committeeInfoMap.put(infoCommittee.getCommitteeId(), infoCommittee);
    }

    public AgendaInfoCommittee getCommittee(String name) {
        return this.committeeInfoMap.get(name);
    }

    public void removeCommittee(String name) {
        this.committeeInfoMap.remove(name);
    }

    /** --- Basic Getters/Setters --- */

    public AgendaId getAgendaId() {
        return agendaId;
    }

    public void setAgendaId(AgendaId agendaId) {
        this.agendaId = agendaId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Date getWeekOf() {
        return weekOf;
    }

    public void setWeekOf(Date weekOf) {
        this.weekOf = weekOf;
    }

    public Map<CommitteeId, AgendaInfoCommittee> getCommitteeInfoMap() {
        return committeeInfoMap;
    }

    public void setCommitteeInfoMap(Map<CommitteeId, AgendaInfoCommittee> committeeInfoMap) {
        this.committeeInfoMap = committeeInfoMap;
    }
}
