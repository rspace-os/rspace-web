<%@ include file="/common/taglibs.jsp" %>

    <head>
        <link rel="stylesheet" href="<c:url value='/scripts/bower_components/photoswipe/dist/photoswipe.css'/>" />
        <link rel="stylesheet"
            href="<c:url value='/scripts/bower_components/photoswipe/dist/default-skin/default-skin.css'/>" />

        <script src="<c:url value='/scripts/bower_components/photoswipe/dist/photoswipe.min.js'/>"></script>
        <script src="<c:url value='/scripts/bower_components/photoswipe/dist/photoswipe-ui-default.min.js'/>"></script>
        <script src="<c:url value='/scripts/pages/photoswipe.js'/>"></script>

        <style>
            .pswp {
                z-index: 10000200 !important;
                /* so it's on top of gallery dialog */
            }

            .pswp__img {
                background-color: white;
            }

            .pswp__caption__center {
                text-align: center;
            }

            .publicNextArrowText,
            .publicPrevArrowText {
                background-color: white;
                text-align: center;
                color:white;
                width:200px;
                padding:15px;
                font-size:20px;
                line-height:18px;
            }
        </style>
    </head>

    <!-- Root element of PhotoSwipe. Must have class pswp. -->
    <div class="pswp" tabindex="-1" role="dialog" aria-hidden="true">

        <!-- Background of PhotoSwipe. 
         It's a separate element as animating opacity is faster than rgba(). -->
        <div class="pswp__bg"></div>

        <!-- Slides wrapper with overflow:hidden. -->
        <div class="pswp__scroll-wrap">

            <!-- Container that holds slides. 
            PhotoSwipe keeps only 3 of them in the DOM to save memory.
            Don't modify these 3 pswp__item elements, data is added later on. -->
            <div class="pswp__container">
                <div class="pswp__item"></div>
                <div class="pswp__item"></div>
                <div class="pswp__item"></div>
            </div>

            <!-- Default (PhotoSwipeUI_Default) interface on top of sliding area. Can be changed. -->
            <div class="pswp__ui pswp__ui--hidden">

                <div class="pswp__top-bar">

                    <!--  Controls are self-explanatory. Order can be changed. -->

                    <div class="pswp__counter"></div>

                    <button class="pswp__button pswp__button--close" title="Close (Esc)"></button>

                    <!-- hiding 'Share' button for RSpace -->
                    <!--  <button class="pswp__button pswp__button--share" title="Share"></button> -->

                    <button class="pswp__button pswp__button--fs" title="Toggle fullscreen"></button>

                    <button class="pswp__button pswp__button--zoom" title="Zoom in/out"></button>

                    <!-- Preloader demo http://codepen.io/dimsemenov/pen/yyBWoR -->
                    <!-- element will get class pswp__preloader--active when preloader is running -->
                    <div class="pswp__preloader">
                        <div class="pswp__preloader__icn">
                            <div class="pswp__preloader__cut">
                                <div class="pswp__preloader__donut"></div>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="pswp__share-modal pswp__share-modal--hidden pswp__single-tap">
                    <div class="pswp__share-tooltip"></div>
                </div>

                <button class="pswp__button pswp__button--arrow--left " title="Previous (arrow left)">
                </button>

                <button class="pswp__button pswp__button--arrow--right " title="Next (arrow right)">
                </button>

                <div class="pswp__caption">
                    <div class="pswp__caption__center"></div>
                </div>

            </div>

        </div>

    </div>
<script>
    $(document).ready(function () {
        <%-- The public view is for a document made accessible to non RSpace users
        The public view will (normally) not load the correct CSS from the photoswipe library
        as it uses a path blocked by SSO, so we display a different set of arrows coded in this jsp.--%>
        const publicView = $("#public_document_view").length > 0;
        if(!publicView) {
            $(".publicPrevArrowText").hide();
            $(".publicNextArrowText").hide();
        }
    });
</script>
