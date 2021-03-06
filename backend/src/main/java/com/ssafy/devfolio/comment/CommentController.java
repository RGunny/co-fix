package com.ssafy.devfolio.comment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ssafy.devfolio.comment.dto.CommentRequest;
import com.ssafy.devfolio.pubsub.RedisListenerService;
import com.ssafy.devfolio.pubsub.RedisSenderService;
import com.ssafy.devfolio.commentroom.service.CommentRoomService;
import com.ssafy.devfolio.exception.BaseException;
import com.ssafy.devfolio.exception.ErrorCode;
import com.ssafy.devfolio.response.ResponseService;
import com.ssafy.devfolio.response.dto.BaseResponse;
import com.ssafy.devfolio.response.dto.ListDataResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/commentRooms/{commentRoomId}/documents/{documentId}")
@Api(value = "코멘트 api. 경로에 문서 id 필수")
public class CommentController {

    private final ResponseService responseService;
    private final CommentService commentService;
    private final CommentRoomService commentRoomService;
    private final RedisSenderService redisSenderService;
    private final RedisListenerService redisListenerService;

    @ApiOperation(value = "문장에 코멘트 작성 (닫힌 첨삭방엔 불가능)", notes = "작성 후 sentence 채널 구독자에게 작성내역 보냄")
    @PostMapping("/sentences/{sentenceId}/comments")
    public ResponseEntity writeComment(@ApiParam(value = "첨삭방 id", required = true) @PathVariable String commentRoomId,
                                       @ApiParam(value = "문서 id", required = true) @PathVariable String documentId,
                                       @ApiParam(value = "문장 id", required = true) @PathVariable String sentenceId,
                                       @ApiParam(value = "코멘트 작성 정보", required = true) @RequestBody CommentRequest request) throws JsonProcessingException {
        if (commentRoomService.isClosedRoom(commentRoomId)) {
            throw new BaseException(ErrorCode.COMMENT_ROOM_CLOSED_EXCEPTION);
        }
        Comment comment = commentService.writeComment(commentRoomId, documentId, sentenceId, request);

        redisSenderService.sendCommentUpdateService(sentenceId, comment);

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @ApiOperation(value = "문장에 달린 코멘트 조회", notes = "조회시 해당 문장에 대한 sentence 채널이 생성됨")
    @GetMapping("/sentences/{sentenceId}/comments")
    public ResponseEntity getComments(@ApiParam(value = "첨삭방 id", required = true) @PathVariable String commentRoomId,
                                      @ApiParam(value = "문서 id", required = true) @PathVariable String documentId,
                                      @ApiParam(value = "문장 id", required = true) @PathVariable String sentenceId) throws JsonProcessingException {
        List<Comment> comments = commentService.getComments(documentId, sentenceId);

        ListDataResponse<Comment> response = responseService.getListDataResponse(comments, HttpStatus.OK);

        redisListenerService.createSentenceTopic(sentenceId);

        return ResponseEntity.status(response.getStatus()).body(response);
    }

    @ApiOperation(value = "코멘트에 agree (이미 한 사람이 요청시 취소, 닫힌 방엔 불가능)", notes = "동의 후 sentence 채널 구독자에게 변경내역 보냄")
    @PostMapping("/sentences/{sentenceId}/comments/{commentId}/agree")
    public ResponseEntity getComments(@ApiParam(value = "첨삭방 id", required = true) @PathVariable String commentRoomId,
                                      @ApiParam(value = "문서 id", required = true) @PathVariable String documentId,
                                      @ApiParam(value = "문장 id", required = true) @PathVariable String sentenceId,
                                      @ApiParam(value = "코멘트 id", required = true) @PathVariable String commentId,
                                      @ApiParam(value = "작성자 닉네임", required = true) @RequestParam(required = true, defaultValue = "") String nickname) throws JsonProcessingException {
        if (commentRoomService.isClosedRoom(commentRoomId)) {
            throw new BaseException(ErrorCode.COMMENT_ROOM_CLOSED_EXCEPTION);
        }

        if (nickname == null || nickname.equals("")) {
            throw new BaseException(ErrorCode.COMMENT_INVALID_NICKNAME_EXCEPTION);
        }

        Comment comment = commentService.pressAgree(sentenceId, commentId, nickname);

        redisSenderService.sendCommentAgreeUpdateService(sentenceId, comment);

        BaseResponse response = responseService.getSuccessResponse();

        return ResponseEntity.status(response.getStatus()).body(response);
    }
}
