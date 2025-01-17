package site.dpbr.dsjs.domain.character.usecase;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import site.dpbr.dsjs.domain.character.domain.Character;
import site.dpbr.dsjs.domain.character.domain.repository.CharacterRepository;
import site.dpbr.dsjs.domain.character.exception.CharacterNotFoundException;
import site.dpbr.dsjs.domain.character.exception.ImageDownloadFailException;
import site.dpbr.dsjs.domain.character.presentation.dto.response.CharacterBasicInfoResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class ExportCharacterImages {

    private final FetchCharacterInfo fetchCharacterInfo;
    private final CharacterRepository characterRepository;

    public byte[] execute(MultipartFile file, String date) {
        try (InputStream inputStream = file.getInputStream()) {

            // Step 1: 이미지 데이터를 저장할 리스트 생성
            List<ImageData> images = new ArrayList<>();
            Workbook workbook = new XSSFWorkbook(inputStream);
            Sheet sheet = workbook.getSheetAt(0); // 첫 번째 시트를 가져옵니다.

            // Step 2: 이미지 다운로드 작업을 병렬로 수행
            ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            List<Future<ImageData>> futures = new ArrayList<>();

            for (Row row : sheet) {
                String name = row.getCell(0).getStringCellValue();
                if (name.isEmpty()) continue;

                String characterName = row.getCell(1).getStringCellValue();
                if (characterName.isEmpty()) continue;

                Character character = characterRepository.findByName(characterName)
                        .orElseThrow(CharacterNotFoundException::new);

                String imageUrl = fetchCharacterInfo.fetchCharacterData("/character/basic?ocid=" + character.getOcid() + "&date=" + date, CharacterBasicInfoResponse.class).characterImage();

                Callable<ImageData> task = () -> {
                    try (InputStream imageStream = downloadImage(imageUrl)) {
                        byte[] imageBytes = imageStream.readAllBytes();
                        return new ImageData(characterName, name, imageBytes);
                    }
                };

                futures.add(executorService.submit(task));
            }

            for (Future<ImageData> future : futures) {
                images.add(future.get());
            }

            executorService.shutdown();

            // Step 3: ZIP 파일 생성 및 ByteArrayOutputStream에 기록
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                for (ImageData imageData : images) {
                    ZipEntry zipEntry = new ZipEntry(imageData.id() + "_" + imageData.name() + ".png");
                    zos.putNextEntry(zipEntry);
                    zos.write(imageData.imageBytes());
                    zos.closeEntry();
                }
            }

            return baos.toByteArray();

        } catch (InterruptedException | ExecutionException | IOException e) {
            throw new ImageDownloadFailException();
        }
    }

    private InputStream downloadImage(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setDoInput(true);

        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new ImageDownloadFailException();
        }

        return connection.getInputStream();
    }

    private record ImageData(String name, String id, byte[] imageBytes) {
    }
}