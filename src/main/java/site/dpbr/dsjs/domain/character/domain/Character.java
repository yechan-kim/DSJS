package site.dpbr.dsjs.domain.character.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import site.dpbr.dsjs.domain.character.presentation.dto.response.CharacterBasicInfoResponse;

import java.util.UUID;

@Getter
@Entity(name = "characters")
@AllArgsConstructor
@NoArgsConstructor
public class Character {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID uuid;

    @Column(nullable = false)
    String ocid;

    @Column(nullable = false)
    String name;

    @Column
    String world;

    @Column
    String job;

    @Column
    Integer level;

    @Column
    Integer unionLevel;

    @Column
    Integer stat;

    @Column
    String gender;

    public static Character create(String ocid, String name) {
        return Character.builder()
                .ocid(ocid)
                .name(name)
                .build();
    }

    @Builder
    public Character(String ocid, String name) {
        this.ocid = ocid;
        this.name = name;
    }

    public void updateBasicInfo(CharacterBasicInfoResponse response) {
        this.world = response.worldName();
        this.job = response.characterClass();
        this.level = response.characterLevel();
        this.gender = response.characterGender();
    }
}
